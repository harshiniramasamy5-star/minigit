package in.nitw.minigit.readmodel;

import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.readmodel.ActivityModels.Activity;
import in.nitw.minigit.readmodel.ActivityModels.ActivityDay;
import in.nitw.minigit.readmodel.ActivityModels.ActivityRepo;
import in.nitw.minigit.readmodel.ActivityModels.GraphBranch;
import in.nitw.minigit.readmodel.ActivityModels.GraphCommit;
import in.nitw.minigit.readmodel.ActivityModels.GraphView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-only queries behind the contribution heatmap and the commit graph, in plain SQL
 * (same approach as RepoReadQueries: no JPA entities, nothing shared with U2/U3/U8 modules).
 */
@Repository
public class ActivityReadQueries {

    static final int DEFAULT_GRAPH_LIMIT = 200;
    static final int MAX_GRAPH_LIMIT = 500;

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityReadQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ heatmap

    /**
     * Contributions of one user: commits they authored (the empty "Initial commit" of a repo is
     * skipped, like on the dashboard), pull requests they opened, and review activity (PR comments
     * and approvals). Repos in the recycle bin are ignored.
     *
     * @param year a calendar year, or null for "the last 365 days up to today" (GitHub's default view)
     */
    public Activity activity(long userId, Integer year) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from;
        LocalDate to;
        if (year == null) {
            to = today;
            from = today.minusDays(364);
        } else {
            if (year < 2000 || year > today.getYear()) {
                throw ApiException.badRequest("VALIDATION_FAILED", "year must be between 2000 and " + today.getYear());
            }
            from = LocalDate.of(year, 1, 1);
            to = LocalDate.of(year, 12, 31);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("from", Timestamp.from(from.atStartOfDay().toInstant(ZoneOffset.UTC)))
                .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));

        record Hit(String day, ContributionKind kind, long repoId) {}
        List<Hit> hits = jdbc.query("""
                SELECT d, kind, repo_id FROM (
                    SELECT DATE(c.created_at) AS d, 'COMMIT' AS kind, c.repo_id AS repo_id
                    FROM commits c JOIN repositories r ON r.id = c.repo_id
                    WHERE c.author_id = :uid AND r.deleted_at IS NULL
                      AND c.created_at >= :from AND c.created_at < :to
                      AND EXISTS (SELECT 1 FROM commit_parents cp
                                  WHERE cp.repo_id = c.repo_id AND cp.commit_hash = c.hash)
                    UNION ALL
                    SELECT DATE(pr.created_at), 'PR', pr.repo_id
                    FROM pull_requests pr JOIN repositories r ON r.id = pr.repo_id
                    WHERE pr.author_id = :uid AND r.deleted_at IS NULL
                      AND pr.created_at >= :from AND pr.created_at < :to
                    UNION ALL
                    SELECT DATE(pc.created_at), 'REVIEW', pr.repo_id
                    FROM pr_comments pc
                    JOIN pull_requests pr ON pr.id = pc.pr_id
                    JOIN repositories r ON r.id = pr.repo_id
                    WHERE pc.author_id = :uid AND r.deleted_at IS NULL
                      AND pc.created_at >= :from AND pc.created_at < :to
                    UNION ALL
                    SELECT DATE(a.approved_at), 'REVIEW', pr.repo_id
                    FROM pr_approvals a
                    JOIN pull_requests pr ON pr.id = a.pr_id
                    JOIN repositories r ON r.id = pr.repo_id
                    WHERE a.user_id = :uid AND r.deleted_at IS NULL
                      AND a.approved_at >= :from AND a.approved_at < :to
                ) x""", params,
                (rs, i) -> new Hit(rs.getString("d"), ContributionKind.fromSql(rs.getString("kind")), rs.getLong("repo_id")));

        Map<String, Integer> perDay = new TreeMap<>();
        Map<Long, Integer> perRepo = new HashMap<>();
        ContributionTally tally = new ContributionTally();
        for (Hit h : hits) {
            perDay.merge(h.day(), 1, Integer::sum);
            perRepo.merge(h.repoId(), 1, Integer::sum);
            tally.record(h.kind());
        }

        Map<LocalDate, Integer> counts = new HashMap<>();
        perDay.forEach((d, n) -> counts.put(LocalDate.parse(d), n));
        ContributionCalendar calendar = new ContributionCalendar(from, to, counts);

        List<ActivityDay> days = new ArrayList<>();
        perDay.forEach((d, n) -> days.add(new ActivityDay(d, n, calendar.levelOn(LocalDate.parse(d)).intensity())));

        return new Activity(from.toString(), to.toString(), hits.size(), yearsFor(userId, today.getYear()),
                days, tally.breakdown(), repoNames(perRepo), calendar.stats(today));
    }

    /** Years the user can pick: this year back to the year the account was created. */
    private List<Integer> yearsFor(long userId, int thisYear) {
        List<Timestamp> created = jdbc.query("SELECT created_at FROM users WHERE id = :uid",
                new MapSqlParameterSource("uid", userId), (rs, i) -> rs.getTimestamp("created_at"));
        int first = thisYear;
        if (!created.isEmpty() && created.get(0) != null) {
            first = Math.min(thisYear, created.get(0).toInstant().atZone(ZoneOffset.UTC).getYear());
        }
        List<Integer> years = new ArrayList<>();
        for (int y = thisYear; y >= first; y--) years.add(y);
        return years;
    }

    private List<ActivityRepo> repoNames(Map<Long, Integer> perRepo) {
        if (perRepo.isEmpty()) return List.of();
        Map<Long, String> names = new HashMap<>();
        jdbc.query("""
                SELECT r.id, CONCAT(u.username, '/', r.name) AS repo
                FROM repositories r JOIN users u ON u.id = r.owner_id
                WHERE r.id IN (:ids)""",
                new MapSqlParameterSource("ids", perRepo.keySet()),
                rs -> { names.put(rs.getLong("id"), rs.getString("repo")); });
        List<ActivityRepo> out = new ArrayList<>();
        perRepo.forEach((id, n) -> { if (names.containsKey(id)) out.add(new ActivityRepo(names.get(id), n)); });
        out.sort((a, b) -> b.count() != a.count() ? Integer.compare(b.count(), a.count()) : a.repo().compareTo(b.repo()));
        return out;
    }

    // ------------------------------------------------------------------ commit graph

    /**
     * The newest {@code limit} commits of a repo (all branches) with their parents, plus every
     * live branch head. Public repos are open to everyone; a private repo is a 404 unless the
     * viewer owns it or has a repo_permissions row (never reveal that it exists).
     */
    public GraphView graph(String owner, String repoName, Optional<Long> viewerId, Integer limit) {
        int max = limit == null ? DEFAULT_GRAPH_LIMIT : Math.max(1, Math.min(limit, MAX_GRAPH_LIMIT));

        List<Map<String, Object>> repos = jdbc.queryForList("""
                SELECT r.id, r.visibility, r.owner_id
                FROM repositories r JOIN users u ON u.id = r.owner_id
                WHERE u.username = :owner AND r.name = :name AND r.deleted_at IS NULL""",
                new MapSqlParameterSource().addValue("owner", owner).addValue("name", repoName));
        if (repos.isEmpty()) throw ApiException.notFound("Repository");
        long repoId = ((Number) repos.get(0).get("id")).longValue();
        boolean isPublic = "PUBLIC".equals(repos.get(0).get("visibility"));
        long ownerId = ((Number) repos.get(0).get("owner_id")).longValue();

        if (!isPublic && !canSee(repoId, ownerId, viewerId)) throw ApiException.notFound("Repository");

        MapSqlParameterSource params = new MapSqlParameterSource("rid", repoId).addValue("limit", max);
        record Row(String hash, String message, String author, Instant createdAt) {}
        List<Row> rows = jdbc.query("""
                SELECT c.hash, c.message, u.username AS author, c.created_at
                FROM commits c JOIN users u ON u.id = c.author_id
                WHERE c.repo_id = :rid
                ORDER BY c.created_at DESC, c.hash DESC LIMIT :limit""", params,
                (rs, i) -> new Row(rs.getString("hash"), rs.getString("message"), rs.getString("author"),
                        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant()));

        Map<String, List<String>> parents = new HashMap<>();
        if (!rows.isEmpty()) {
            List<String> hashes = rows.stream().map(Row::hash).toList();
            jdbc.query("""
                    SELECT commit_hash, parent_hash FROM commit_parents
                    WHERE repo_id = :rid AND commit_hash IN (:hashes)
                    ORDER BY commit_hash, parent_order""",
                    new MapSqlParameterSource("rid", repoId).addValue("hashes", hashes),
                    rs -> { parents.computeIfAbsent(rs.getString("commit_hash"), k -> new ArrayList<>())
                            .add(rs.getString("parent_hash")); });
        }

        Map<String, Row> byHash = new LinkedHashMap<>();
        rows.forEach(r -> byHash.put(r.hash(), r));
        List<GraphOrder.Node> ordered = GraphOrder.dateOrder(rows.stream()
                .map(r -> new GraphOrder.Node(r.hash(), r.createdAt(), parents.getOrDefault(r.hash(), List.of())))
                .toList());
        List<GraphCommit> commits = ordered.stream().map(n -> {
            Row r = byHash.get(n.hash());
            return new GraphCommit(r.hash(), n.parents(), r.message(), r.author(), r.createdAt());
        }).toList();

        List<GraphBranch> branches = jdbc.query("""
                SELECT name, head_commit_hash FROM branches
                WHERE repo_id = :rid AND deleted_at IS NULL ORDER BY name""",
                new MapSqlParameterSource("rid", repoId),
                (rs, i) -> new GraphBranch(rs.getString("name"), rs.getString("head_commit_hash")));

        return new GraphView(commits, branches);
    }

    private boolean canSee(long repoId, long ownerId, Optional<Long> viewerId) {
        if (viewerId.isEmpty()) return false;
        long uid = viewerId.get();
        if (uid == ownerId) return true;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM repo_permissions WHERE repo_id = :rid AND user_id = :uid",
                new MapSqlParameterSource("rid", repoId).addValue("uid", uid), Integer.class);
        return n != null && n > 0;
    }
}

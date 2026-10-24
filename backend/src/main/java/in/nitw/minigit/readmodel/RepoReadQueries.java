package in.nitw.minigit.readmodel;

import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.readmodel.ReadModels.CommitSummary;
import in.nitw.minigit.readmodel.ReadModels.Dashboard;
import in.nitw.minigit.readmodel.ReadModels.PullSummary;
import in.nitw.minigit.readmodel.ReadModels.RepoSummary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Read-only queries for explore + dashboard, in plain SQL.
 * Deliberately no JPA entities here: Repository/Commit/PR entities belong to U2/U3/U8,
 * and duplicating them would mean two mappings of the same table drifting apart.
 * Every query here was run against V1__init_schema.sql on MySQL 8.0.
 */
@Repository
public class RepoReadQueries {

    static final int DASHBOARD_REPOS = 20;
    static final int DASHBOARD_PULLS = 10;
    static final int DASHBOARD_COMMITS = 10;

    private static final String REPO_COLUMNS = """
            r.id, u.username AS owner, r.name, r.description, r.visibility, r.default_branch,
            r.created_at, r.updated_at""";

    /** Repos the user owns or has any repo_permissions row for, not in the recycle bin. */
    private static final String MY_REPOS_SCOPE = """
            LEFT JOIN repo_permissions p ON p.repo_id = r.id AND p.user_id = :uid
            WHERE r.deleted_at IS NULL AND (r.owner_id = :uid OR p.user_id IS NOT NULL)""";

    private final NamedParameterJdbcTemplate jdbc;

    public RepoReadQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Public, non-deleted repos; optional case-insensitive match on name or description. */
    public PageResponse<RepoSummary> explore(String query, int page, int size) {
        String pattern = LikePattern.contains(query);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", PageResponse.offset(page, size));

        String where = "WHERE r.visibility = 'PUBLIC' AND r.deleted_at IS NULL";
        if (pattern != null) {
            where += " AND (r.name LIKE :pattern ESCAPE '!' OR r.description LIKE :pattern ESCAPE '!')";
            params.addValue("pattern", pattern);
        }
        String from = " FROM repositories r JOIN users u ON u.id = r.owner_id " + where;

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, params, Long.class);
        List<RepoSummary> items = (total == null || total == 0) ? List.of() : jdbc.query(
                "SELECT " + REPO_COLUMNS + ", NULL AS my_role" + from
                        + " ORDER BY r.updated_at DESC, r.id DESC LIMIT :limit OFFSET :offset",
                params, REPO_MAPPER);
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public Dashboard dashboard(long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("uid", userId);

        List<RepoSummary> repos = jdbc.query("""
                SELECT %s, CASE WHEN r.owner_id = :uid THEN 'OWNER' ELSE p.role END AS my_role
                FROM repositories r JOIN users u ON u.id = r.owner_id
                %s
                ORDER BY r.updated_at DESC, r.id DESC LIMIT %d"""
                .formatted(REPO_COLUMNS, MY_REPOS_SCOPE, DASHBOARD_REPOS), params, REPO_MAPPER);

        List<PullSummary> pulls = jdbc.query("""
                SELECT CONCAT(u.username, '/', r.name) AS repo, pr.number, pr.title,
                       au.username AS author, pr.source_branch_name, pr.target_branch_name, pr.created_at
                FROM pull_requests pr
                JOIN repositories r ON r.id = pr.repo_id
                JOIN users u ON u.id = r.owner_id
                JOIN users au ON au.id = pr.author_id
                %s AND pr.status = 'OPEN'
                ORDER BY pr.created_at DESC, pr.id DESC LIMIT %d"""
                .formatted(MY_REPOS_SCOPE, DASHBOARD_PULLS), params,
                (rs, i) -> new PullSummary(rs.getString("repo"), rs.getInt("number"),
                        rs.getString("title"), rs.getString("author"),
                        rs.getString("source_branch_name"), rs.getString("target_branch_name"),
                        instant(rs.getTimestamp("created_at"))));

        // Root commits (no parents) are skipped: every repo starts with an empty
        // "Initial commit" (SRS decision) and listing those is just noise.
        List<CommitSummary> commits = jdbc.query("""
                SELECT CONCAT(u.username, '/', r.name) AS repo, c.hash, c.message,
                       au.username AS author, c.created_at
                FROM commits c
                JOIN repositories r ON r.id = c.repo_id
                JOIN users u ON u.id = r.owner_id
                JOIN users au ON au.id = c.author_id
                %s AND EXISTS (SELECT 1 FROM commit_parents cp
                               WHERE cp.repo_id = c.repo_id AND cp.commit_hash = c.hash)
                ORDER BY c.created_at DESC LIMIT %d"""
                .formatted(MY_REPOS_SCOPE, DASHBOARD_COMMITS), params,
                (rs, i) -> new CommitSummary(rs.getString("repo"), rs.getString("hash"),
                        rs.getString("message"), rs.getString("author"),
                        instant(rs.getTimestamp("created_at"))));

        return new Dashboard(repos, pulls, commits);
    }

    private static final RowMapper<RepoSummary> REPO_MAPPER = (rs, i) -> new RepoSummary(
            rs.getLong("id"), rs.getString("owner"), rs.getString("name"),
            rs.getString("description"), rs.getString("visibility"), rs.getString("default_branch"),
            rs.getString("my_role"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

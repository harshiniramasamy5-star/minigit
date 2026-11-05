package in.nitw.minigit.pr;

import in.nitw.minigit.branch.BranchProtection;
import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.history.RepoHistory;
import in.nitw.minigit.merge.BranchesMerged;
import in.nitw.minigit.merge.MergeEngine;
import in.nitw.minigit.merge.MergeService;
import in.nitw.minigit.storage.RepoObjectStores;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * U8: pull requests. States OPEN / MERGED / CLOSED (V1 schema). Approval is recorded in pr_approvals and is optional,
 * except when the target branch has require_approval on (BranchProtection).
 * An approval counts for the source branch head it was given on; new commits on the source reset it. The merge itself is U6's MergeService; a PR turns MERGED when that service reports a merge of
 * its branch pair (BranchesMerged event), so conflicts resolved later in a merge session are covered too.
 */
@Service
public class PullRequestService {

    public record PullView(int number, String title, String description, String author, String source, String target,
                           String status, List<String> approvals, Instant createdAt, Instant closedAt,
                           String mergeCommit) {}

    public record CommentView(long id, String author, String body, Instant createdAt) {}

    public record PullDetail(PullView pr, List<RepoHistory.FileDiffView> diff, List<CommentView> comments) {}

    /** result: UP_TO_DATE | FAST_FORWARD | MERGED | CONFLICTS. sessionId only for CONFLICTS (see /merges). */
    public record MergeOutcome(String result, String commit, String sessionId, PullView pr) {}

    private record Row(long id, int number, long authorId, String author, String source, String target,
                       String title, String description, String status, String mergeCommit,
                       Instant createdAt, Instant closedAt) {}

    private static final String SELECT = "SELECT p.id, p.number, p.author_id, a.username AS author, "
            + "p.source_branch_name, p.target_branch_name, p.title, p.description, p.status, p.merge_commit_hash, "
            + "p.created_at, p.closed_at FROM pull_requests p JOIN users a ON a.id = p.author_id ";

    private static Row map(ResultSet rs, int i) throws SQLException {
        Timestamp closed = rs.getTimestamp("closed_at");
        return new Row(rs.getLong("id"), rs.getInt("number"), rs.getLong("author_id"), rs.getString("author"),
                rs.getString("source_branch_name"), rs.getString("target_branch_name"), rs.getString("title"),
                rs.getString("description"), rs.getString("status"), rs.getString("merge_commit_hash"),
                rs.getTimestamp("created_at").toInstant(), closed == null ? null : closed.toInstant());
    }

    private final JdbcTemplate jdbc;
    private final BranchStore branches;
    private final MergeService merges;
    private final RepoObjectStores stores;
    private final RepoHistory history;
    private final BranchProtection protection;

    public PullRequestService(JdbcTemplate jdbc, BranchStore branches, MergeService merges,
                              RepoObjectStores stores, RepoHistory history, BranchProtection protection) {
        this.jdbc = jdbc;
        this.branches = branches;
        this.merges = merges;
        this.stores = stores;
        this.history = history;
        this.protection = protection;
    }

    @Transactional
    public PullView create(long repoId, long userId, String title, String description, String source, String target) {
        PullRules.checkNew(title, description, source, target);
        BranchStore.Branch from = branches.find(repoId, source)
                .orElseThrow(() -> ApiException.notFound("Branch " + source));
        BranchStore.Branch into = branches.find(repoId, target)
                .orElseThrow(() -> ApiException.notFound("Branch " + target));

        Long open = jdbc.queryForObject("SELECT COUNT(*) FROM pull_requests WHERE repo_id = ? "
                + "AND source_branch_name = ? AND target_branch_name = ? AND status = 'OPEN'",
                Long.class, repoId, source, target);
        if (open != null && open > 0) {
            throw ApiException.conflict("PR_EXISTS", "There is already an open pull request from " + source + " to " + target);
        }
        try {
            if (new MergeEngine(stores.history(repoId)).plan(into.headCommitHash(), from.headCommitHash()).outcome()
                    == MergeEngine.Outcome.UP_TO_DATE) {
                throw ApiException.badRequest("NOTHING_TO_MERGE", target + " already contains everything in " + source);
            }
        } catch (IllegalStateException unrelatedHistories) {
            // no common ancestor: let the merge itself report it
        }

        String desc = (description == null || description.isBlank()) ? null : description.strip();
        for (int attempt = 0; ; attempt++) {
            try {
                Integer next = jdbc.queryForObject(
                        "SELECT COALESCE(MAX(number), 0) + 1 FROM pull_requests WHERE repo_id = ?", Integer.class, repoId);
                jdbc.update("INSERT INTO pull_requests (repo_id, number, author_id, source_branch_id, target_branch_id, "
                                + "source_branch_name, target_branch_name, title, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        repoId, next, userId, from.id(), into.id(), source, target, title.strip(), desc);
                return view(require(repoId, next));
            } catch (DuplicateKeyException raced) {
                if (attempt >= 2) throw ApiException.conflict("CONFLICT", "Another pull request was created at the same time. Try again.");
            }
        }
    }

    /** @param status OPEN / MERGED / CLOSED, or null for all */
    public PageResponse<PullView> list(long repoId, String status, int page, int size) {
        String where = status == null ? "WHERE p.repo_id = ? " : "WHERE p.repo_id = ? AND p.status = ? ";
        Object[] countArgs = status == null ? new Object[]{repoId} : new Object[]{repoId, status};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM pull_requests p " + where, Long.class, countArgs);
        List<Object> args = new ArrayList<>(List.of(countArgs));
        args.add(size);
        args.add(PageResponse.offset(page, size));
        List<Row> rows = jdbc.query(SELECT + where + "ORDER BY p.number DESC LIMIT ? OFFSET ?",
                PullRequestService::map, args.toArray());
        return new PageResponse<>(rows.stream().map(this::view).toList(), page, size, total == null ? 0 : total);
    }

    public PullDetail detail(long repoId, String defaultBranch, int number) {
        Row row = require(repoId, number);
        return new PullDetail(view(row), diffOf(repoId, defaultBranch, row), comments(row.id()));
    }

    @Transactional
    public CommentView comment(long repoId, long userId, String username, int number, String body) {
        Row row = require(repoId, number);
        PullRules.requireOpen(row.status());
        String text = PullRules.cleanComment(body);
        jdbc.update("INSERT INTO pr_comments (pr_id, author_id, body) VALUES (?, ?, ?)", row.id(), userId, text);
        return comments(row.id()).stream().reduce((first, second) -> second).orElseThrow();
    }

    @Transactional
    public void approve(long repoId, long userId, String username, int number) {
        Row row = require(repoId, number);
        PullRules.requireOpen(row.status());
        PullRules.checkApprover(row.author(), username);
        String head = branches.find(repoId, row.source())
                .orElseThrow(() -> ApiException.notFound("Branch " + row.source())).headCommitHash();
        jdbc.update("INSERT INTO pr_approvals (pr_id, user_id, source_head_hash) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE source_head_hash = VALUES(source_head_hash), approved_at = CURRENT_TIMESTAMP(3)",
                row.id(), userId, head);
    }

    @Transactional
    public PullView close(long repoId, int number) {
        Row row = require(repoId, number);
        PullRules.requireOpen(row.status());
        jdbc.update("UPDATE pull_requests SET status = 'CLOSED', closed_at = ? WHERE id = ? AND status = 'OPEN'",
                Timestamp.from(Instant.now()), row.id());
        return view(require(repoId, number));
    }

    /** Whether the user may close it: Write access or the author. Checked by the controller with this. */
    public long authorIdOf(long repoId, int number) {
        return require(repoId, number).authorId();
    }

    @Transactional
    public MergeOutcome merge(long repoId, long userId, String username, int number, String message) {
        Row row = require(repoId, number);
        PullRules.requireOpen(row.status());
        if (protection.isRequired(repoId, row.target())
                && !protection.hasApprovedOpenPr(repoId, row.source(), row.target())) {
            throw BranchProtection.needsApproval(row.target());
        }
        MergeService.MergeResult r = merges.merge(repoId, userId, username, row.source(), row.target(), null, message);
        switch (r.status()) {
            case "UP_TO_DATE", "FAST_FORWARD", "MERGED" -> markMerged(row.id(), r.commitHash());   // idempotent
            default -> { /* CONFLICTS: stays OPEN until the merge session is finished */ }
        }
        String sessionId = r.session() == null ? null : r.session().sessionId();
        return new MergeOutcome(r.status(), r.commitHash(), sessionId, view(require(repoId, number)));
    }

    /** Any merge of this branch pair (here, via /merges, or a finished conflict session) closes the matching open PR. */
    @EventListener
    public void onMerged(BranchesMerged e) {
        jdbc.update("UPDATE pull_requests SET status = 'MERGED', merge_commit_hash = ?, closed_at = ? "
                        + "WHERE repo_id = ? AND source_branch_name = ? AND target_branch_name = ? AND status = 'OPEN'",
                e.commitHash(), Timestamp.from(Instant.now()), e.repoId(), e.sourceBranch(), e.targetBranch());
    }

    private void markMerged(long id, String commitHash) {
        jdbc.update("UPDATE pull_requests SET status = 'MERGED', merge_commit_hash = ?, closed_at = ? "
                + "WHERE id = ? AND status = 'OPEN'", commitHash, Timestamp.from(Instant.now()), id);
    }

    /** OPEN: what the merge would bring in (merge base to source head). Merged PRs link to the merge commit instead. */
    private List<RepoHistory.FileDiffView> diffOf(long repoId, String defaultBranch, Row row) {
        if ("MERGED".equals(row.status())) return List.of();
        var from = branches.find(repoId, row.source());
        var into = branches.find(repoId, row.target());
        if (from.isEmpty() || into.isEmpty()) return List.of();
        try {
            String base = new MergeEngine(stores.history(repoId))
                    .plan(into.get().headCommitHash(), from.get().headCommitHash()).base();
            return history.diff(repoId, base, from.get().headCommitHash(), defaultBranch);
        } catch (IllegalStateException unrelatedHistories) {
            return List.of();
        }
    }

    private Row require(long repoId, int number) {
        return jdbc.query(SELECT + "WHERE p.repo_id = ? AND p.number = ?", PullRequestService::map, repoId, number)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Pull request"));
    }

    private PullView view(Row r) {
        List<String> approvals = jdbc.queryForList("SELECT u.username FROM pr_approvals pa "
                + "JOIN users u ON u.id = pa.user_id JOIN pull_requests p ON p.id = pa.pr_id "
                + "LEFT JOIN branches b ON b.repo_id = p.repo_id AND b.name = p.source_branch_name AND b.deleted_at IS NULL "
                + "WHERE pa.pr_id = ? AND (p.status <> 'OPEN' OR pa.source_head_hash = b.head_commit_hash) "
                + "ORDER BY pa.approved_at, u.username", String.class, r.id());
        return new PullView(r.number(), r.title(), r.description(), r.author(), r.source(), r.target(), r.status(),
                approvals, r.createdAt(), r.closedAt(), r.mergeCommit());
    }

    private List<CommentView> comments(long prId) {
        return jdbc.query("SELECT c.id, u.username, c.body, c.created_at FROM pr_comments c "
                        + "JOIN users u ON u.id = c.author_id WHERE c.pr_id = ? ORDER BY c.created_at, c.id",
                (rs, i) -> new CommentView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
                prId);
    }
}

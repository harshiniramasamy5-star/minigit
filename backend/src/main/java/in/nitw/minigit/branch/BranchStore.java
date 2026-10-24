package in.nitw.minigit.branch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Branch heads live in the V1 {@code branches} table, the single source of truth.
 * (Replaces Aakriti's file-based BranchRefStore.)
 *
 * Head changes go through {@link #moveHead}, a compare-and-set: the UPDATE only
 * applies if the head is still what the caller read. 0 rows = someone moved it first,
 * which callers turn into 409 BRANCH_HEAD_MOVED.
 */
@Repository
public class BranchStore {

    public record Branch(long id, long repoId, String name, String headCommitHash, boolean isProtected) {}

    private final JdbcTemplate jdbc;

    public BranchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Branch map(ResultSet rs, int row) throws SQLException {
        return new Branch(rs.getLong("id"), rs.getLong("repo_id"), rs.getString("name"),
                rs.getString("head_commit_hash"), rs.getBoolean("is_protected"));
    }

    public List<Branch> list(long repoId) {
        return jdbc.query("SELECT id, repo_id, name, head_commit_hash, is_protected FROM branches "
                + "WHERE repo_id = ? AND deleted_at IS NULL ORDER BY name", BranchStore::map, repoId);
    }

    public Optional<Branch> find(long repoId, String name) {
        return jdbc.query("SELECT id, repo_id, name, head_commit_hash, is_protected FROM branches "
                + "WHERE repo_id = ? AND name = ? AND deleted_at IS NULL", BranchStore::map, repoId, name).stream().findFirst();
    }

    /** True if a branch with this name is in the recycle bin (it still owns the unique (repo, name) key). */
    public boolean isInRecycleBin(long repoId, String name) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM branches WHERE repo_id = ? AND name = ? "
                + "AND deleted_at IS NOT NULL", Long.class, repoId, name);
        return n != null && n > 0;
    }

    public void create(long repoId, String name, String headCommitHash) {
        jdbc.update("INSERT INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (?, ?, ?, FALSE)",
                repoId, name, headCommitHash);
    }

    /** @return true if the head was still {@code expected} and is now {@code next}. */
    public boolean moveHead(long repoId, String name, String expected, String next) {
        return jdbc.update("UPDATE branches SET head_commit_hash = ? "
                + "WHERE repo_id = ? AND name = ? AND head_commit_hash = ?",
                next, repoId, name, expected) == 1;
    }

    /**
     * U11: a delete is a move to the recycle bin (branches.deleted_at + a recycle_bin row, kept 7 days).
     * Restore or the daily cleanup in the recycle package takes it from there.
     */
    public void softDelete(long repoId, String name, long deletedBy) {
        Optional<Branch> b = find(repoId, name);
        if (b.isEmpty()) return;
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        java.sql.Timestamp expires = java.sql.Timestamp.from(java.time.Instant.now().plus(java.time.Duration.ofDays(7)));
        jdbc.update("UPDATE branches SET deleted_at = ? WHERE id = ?", now, b.get().id());
        jdbc.update("INSERT INTO recycle_bin (item_type, item_id, repo_id, deleted_by, deleted_at, expires_at) "
                + "VALUES ('BRANCH', ?, ?, ?, ?, ?)", b.get().id(), repoId, deletedBy, now, expires);
    }
}

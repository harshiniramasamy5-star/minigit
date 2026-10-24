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
                + "WHERE repo_id = ? ORDER BY name", BranchStore::map, repoId);
    }

    public Optional<Branch> find(long repoId, String name) {
        return jdbc.query("SELECT id, repo_id, name, head_commit_hash, is_protected FROM branches "
                + "WHERE repo_id = ? AND name = ?", BranchStore::map, repoId, name).stream().findFirst();
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

    public void delete(long repoId, String name) {
        jdbc.update("DELETE FROM branches WHERE repo_id = ? AND name = ?", repoId, name);
    }
}

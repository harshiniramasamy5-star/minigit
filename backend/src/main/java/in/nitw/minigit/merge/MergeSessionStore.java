package in.nitw.minigit.merge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Merge sessions in the merge_sessions / merge_conflicts tables (V1 schema), so a half-resolved
 * merge survives a restart. Only the hashes and the per-file choices are stored: the clean files and
 * the conflicts are recomputed from the three commits when a session is loaded.
 * A session expires one hour after it was started.
 */
@Repository
public class MergeSessionStore {

    private static final String LIVE = "s.status = 'OPEN' AND s.created_at > (NOW(3) - INTERVAL 1 HOUR)";

    public record Row(long id, long repoId, long userId, String sourceBranch, String targetBranch,
                      String baseHash, String oursHash, String theirsHash, Instant createdAt) {}

    /** resolution: UNRESOLVED | OURS | THEIRS | MANUAL. blobHash is null for UNRESOLVED and for "deleted". */
    public record Stored(String path, String resolution, String blobHash) {}

    private final JdbcTemplate jdbc;

    public MergeSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Frees the slot of sessions nobody finished within the hour. */
    public void expireOld() {
        jdbc.update("UPDATE merge_sessions SET status = 'ABORTED', closed_at = NOW(3) "
                + "WHERE status = 'OPEN' AND created_at <= (NOW(3) - INTERVAL 1 HOUR)");
    }

    public boolean hasOpen(long repoId, long targetBranchId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM merge_sessions s WHERE s.repo_id = ? "
                + "AND s.target_branch_id = ? AND " + LIVE, Integer.class, repoId, targetBranchId);
        return n != null && n > 0;
    }

    public long insert(long repoId, long userId, long sourceBranchId, long targetBranchId,
                       String baseHash, String oursHash, String theirsHash,
                       Map<String, FileConflict> conflicts) {
        jdbc.update("INSERT INTO merge_sessions (repo_id, user_id, source_branch_id, target_branch_id, "
                + "base_commit_hash, ours_commit_hash, theirs_commit_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
                repoId, userId, sourceBranchId, targetBranchId, baseHash, oursHash, theirsHash);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        for (FileConflict c : conflicts.values()) {
            jdbc.update("INSERT INTO merge_conflicts (session_id, path, conflict_type) VALUES (?, ?, ?)",
                    id, c.path(), c.kind().name());
        }
        return id;
    }

    public Optional<Row> findOpen(long repoId, long sessionId) {
        return jdbc.query("SELECT s.id, s.repo_id, s.user_id, sb.name AS source_name, tb.name AS target_name, "
                        + "s.base_commit_hash, s.ours_commit_hash, s.theirs_commit_hash, s.created_at "
                        + "FROM merge_sessions s "
                        + "JOIN branches sb ON sb.id = s.source_branch_id "
                        + "JOIN branches tb ON tb.id = s.target_branch_id "
                        + "WHERE s.id = ? AND s.repo_id = ? AND " + LIVE,
                (rs, i) -> new Row(rs.getLong("id"), rs.getLong("repo_id"), rs.getLong("user_id"),
                        rs.getString("source_name"), rs.getString("target_name"),
                        rs.getString("base_commit_hash"), rs.getString("ours_commit_hash"),
                        rs.getString("theirs_commit_hash"), rs.getTimestamp("created_at").toInstant()),
                sessionId, repoId).stream().findFirst();
    }

    public List<Stored> conflicts(long sessionId) {
        return jdbc.query("SELECT path, resolution, resolved_blob_hash FROM merge_conflicts "
                        + "WHERE session_id = ? ORDER BY path",
                (rs, i) -> new Stored(rs.getString("path"), rs.getString("resolution"),
                        rs.getString("resolved_blob_hash")), sessionId);
    }

    public void saveResolution(long sessionId, String path, String resolution, String blobHash) {
        jdbc.update("UPDATE merge_conflicts SET resolution = ?, resolved_blob_hash = ? "
                + "WHERE session_id = ? AND path = ?", resolution, blobHash, sessionId, path);
    }

    /** status: COMPLETED or ABORTED. */
    public void close(long sessionId, String status) {
        jdbc.update("UPDATE merge_sessions SET status = ?, closed_at = NOW(3) WHERE id = ?", status, sessionId);
    }
}

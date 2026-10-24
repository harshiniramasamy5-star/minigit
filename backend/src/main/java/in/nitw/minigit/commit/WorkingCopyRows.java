package in.nitw.minigit.commit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** The working_copies table (V1): which branch a user has checked out in a repo, and the head they started from. */
@Repository
public class WorkingCopyRows {

    public record Row(long branchId, String branchName, String baseCommitHash) {}

    private final JdbcTemplate jdbc;

    public WorkingCopyRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Empty if the user has none yet, or the branch they were on is in the recycle bin. */
    public Optional<Row> find(long userId, long repoId) {
        return jdbc.query("SELECT w.branch_id, b.name, w.base_commit_hash FROM working_copies w "
                        + "JOIN branches b ON b.id = w.branch_id "
                        + "WHERE w.user_id = ? AND w.repo_id = ? AND b.deleted_at IS NULL",
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3)), userId, repoId)
                .stream().findFirst();
    }

    public void save(long userId, long repoId, long branchId, String baseCommitHash) {
        jdbc.update("INSERT INTO working_copies (user_id, repo_id, branch_id, base_commit_hash) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE branch_id = VALUES(branch_id), base_commit_hash = VALUES(base_commit_hash)",
                userId, repoId, branchId, baseCommitHash);
    }
}

package in.nitw.minigit.branch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Writes the commits / commit_parents rows for a commit object that is already in the store.
 * created_at must be the same Instant that is inside the commit object (StoredCommit.createdAt()).
 * If U3's commit code already has a DAO for this, point MergeService at it and delete this class.
 */
@Repository
public class CommitRows {

    private final JdbcTemplate jdbc;

    public CommitRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long repoId, String hash, String treeHash, long authorId, String message,
                       Instant createdAt, List<String> parents) {
        jdbc.update("INSERT INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", repoId, hash, treeHash, authorId, message, Timestamp.from(createdAt));
        for (int i = 0; i < parents.size(); i++) {
            jdbc.update("INSERT INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) "
                    + "VALUES (?, ?, ?, ?)", repoId, hash, parents.get(i), i);
        }
    }
}

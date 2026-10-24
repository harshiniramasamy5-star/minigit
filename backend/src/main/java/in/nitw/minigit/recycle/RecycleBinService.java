package in.nitw.minigit.recycle;

import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.repo.RepoService;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.undo.UndoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * U11: deleted repos and branches wait here for 7 days. Restore brings them back untouched,
 * "delete forever" and the daily cleanup remove the rows (and a repo's folder in the file store).
 */
@Service
public class RecycleBinService {

    private static final Logger log = LoggerFactory.getLogger(RecycleBinService.class);

    public record Item(long id, String itemType, String name, String repo, Instant deletedAt, Instant expiresAt) {}

    private record Row(long id, String type, long itemId, long repoId, long ownerId, long deletedBy,
                       boolean repoDeleted) {}

    private final JdbcTemplate jdbc;
    private final RepoObjectStores stores;
    private final UndoService undo;
    private final TransactionTemplate tx;

    public RecycleBinService(JdbcTemplate jdbc, RepoObjectStores stores, UndoService undo,
                             PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.stores = stores;
        this.undo = undo;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Items the user deleted, plus anything deleted from repos they own. Expired-but-not-yet-cleaned rows are hidden. */
    public List<Item> list(long userId) {
        return jdbc.query("SELECT rb.id, rb.item_type, rb.deleted_at, rb.expires_at, r.name AS repo_name, "
                        + "ou.username AS owner, b.name AS branch_name "
                        + "FROM recycle_bin rb JOIN repositories r ON r.id = rb.repo_id "
                        + "JOIN users ou ON ou.id = r.owner_id "
                        + "LEFT JOIN branches b ON rb.item_type = 'BRANCH' AND b.id = rb.item_id "
                        + "WHERE (rb.deleted_by = ? OR r.owner_id = ?) AND rb.expires_at > ? "
                        + "ORDER BY rb.deleted_at DESC, rb.id DESC",
                (rs, i) -> {
                    String type = rs.getString("item_type");
                    String repoName = rs.getString("repo_name");
                    String name = "BRANCH".equals(type) ? rs.getString("branch_name") : repoName;
                    return new Item(rs.getLong("id"), type, name, rs.getString("owner") + "/" + repoName,
                            rs.getTimestamp("deleted_at").toInstant(), rs.getTimestamp("expires_at").toInstant());
                }, userId, userId, Timestamp.from(Instant.now()));
    }

    public void restore(long id, long userId) {
        Row row = mine(id, userId);
        tx.executeWithoutResult(s -> {
            if (row.type().equals("REPOSITORY")) {
                jdbc.update("UPDATE repositories SET deleted_at = NULL WHERE id = ?", row.itemId());
            } else {
                if (row.repoDeleted()) {
                    throw new ApiException(HttpStatus.CONFLICT, "REPO_IN_RECYCLE_BIN",
                            "Restore the repository first, then its branch");
                }
                jdbc.update("UPDATE branches SET deleted_at = NULL WHERE id = ?", row.itemId());
            }
            jdbc.update("DELETE FROM recycle_bin WHERE id = ?", row.id());
        });
    }

    /** "Delete forever" from the bin. */
    public void purge(long id, long userId) {
        purgeRow(mine(id, userId));
    }

    /** Runs every day at 03:00: removes everything past its 7 days. Branches first, then repositories. */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpired() {
        int removed = cleanupExpired(Instant.now());
        if (removed > 0) log.info("Recycle bin cleanup removed {} item(s)", removed);
    }

    /** @return how many items were removed. One bad item never stops the rest. */
    public int cleanupExpired(Instant now) {
        List<Row> expired = jdbc.query(ROW_SQL + "WHERE rb.expires_at <= ? ORDER BY rb.item_type = 'REPOSITORY', rb.id",
                RecycleBinService::mapRow, Timestamp.from(now));
        int removed = 0;
        for (Row row : expired) {
            try {
                purgeRow(row);
                removed++;
            } catch (RuntimeException e) {
                log.warn("Recycle bin cleanup failed for item {}", row.id(), e);
            }
        }
        return removed;
    }

    private void purgeRow(Row row) {
        if (row.type().equals("REPOSITORY")) {
            List<String> branchNames = jdbc.queryForList("SELECT name FROM branches WHERE repo_id = ?",
                    String.class, row.itemId());
            // ON DELETE CASCADE removes commits, branches, PRs, permissions and the other bin rows of this repo.
            tx.executeWithoutResult(s -> jdbc.update("DELETE FROM repositories WHERE id = ?", row.itemId()));
            branchNames.forEach(b -> undo.forget(row.itemId(), b));
            RepoService.deleteDirectory(stores.repoDir(row.itemId()));
        } else {
            tx.executeWithoutResult(s -> {
                jdbc.update("DELETE FROM branches WHERE id = ?", row.itemId());
                jdbc.update("DELETE FROM recycle_bin WHERE id = ?", row.id());
            });
        }
    }

    private static final String ROW_SQL = "SELECT rb.id, rb.item_type, rb.item_id, rb.repo_id, r.owner_id, rb.deleted_by, "
            + "r.deleted_at IS NOT NULL AS repo_deleted FROM recycle_bin rb JOIN repositories r ON r.id = rb.repo_id ";

    private static Row mapRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getString("item_type"), rs.getLong("item_id"), rs.getLong("repo_id"),
                rs.getLong("owner_id"), rs.getLong("deleted_by"), rs.getBoolean("repo_deleted"));
    }

    /**
     * The bin item, if the user may act on it: a repo item only by the repo's owner, a branch item by whoever
     * deleted it or the repo's owner. Anything else is a 404 so ids can't be probed.
     */
    private Row mine(long id, long userId) {
        Row row = jdbc.query(ROW_SQL + "WHERE rb.id = ?", RecycleBinService::mapRow, id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Recycle bin item"));
        boolean allowed = row.type().equals("REPOSITORY")
                ? row.ownerId() == userId
                : row.ownerId() == userId || row.deletedBy() == userId;
        if (!allowed) {
            throw ApiException.notFound("Recycle bin item");
        }
        return row;
    }
}

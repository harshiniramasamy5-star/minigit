package in.nitw.minigit.repo;

import in.nitw.minigit.branch.CommitRows;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.storage.StoredCommit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** U2: create, read, rename/describe, delete (to the recycle bin) repositories. */
@Service
public class RepoService {

    public static final Duration BIN_RETENTION = Duration.ofDays(7);

    public record RepoView(long id, String owner, String name, String description, String visibility,
                           String defaultBranch, String myRole, Instant createdAt, Instant updatedAt) {}

    /** The row plus the owner's username; internal use by the other repo-level services. */
    public record Found(long id, long ownerId, String owner, String name, String description,
                        String visibility, String defaultBranch, Instant createdAt, Instant updatedAt) {}

    private final JdbcTemplate jdbc;
    private final RepoObjectStores stores;
    private final CommitRows commitRows;

    public RepoService(JdbcTemplate jdbc, RepoObjectStores stores, CommitRows commitRows) {
        this.jdbc = jdbc;
        this.stores = stores;
        this.commitRows = commitRows;
    }

    private static final String SELECT = "SELECT r.id, r.owner_id, u.username AS owner, r.name, r.description, "
            + "r.visibility, r.default_branch, r.created_at, r.updated_at "
            + "FROM repositories r JOIN users u ON u.id = r.owner_id ";

    private static Found map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Found(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("owner"), rs.getString("name"),
                rs.getString("description"), rs.getString("visibility"), rs.getString("default_branch"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    /** Active (not in the recycle bin) repo by owner username + name. */
    public Optional<Found> find(String owner, String name) {
        return jdbc.query(SELECT + "WHERE u.username = ? AND r.name = ? AND r.deleted_at IS NULL",
                RepoService::map, owner, name).stream().findFirst();
    }

    /** @return the caller's role on the repo: OWNER / WRITE / READ, or null for none. */
    public String roleOf(long repoId, Long userId) {
        if (userId == null) return null;
        List<String> roles = jdbc.queryForList(
                "SELECT role FROM repo_permissions WHERE repo_id = ? AND user_id = ?", String.class, repoId, userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    /**
     * The repo the caller may see. Private repos the caller has no role on come back as 404
     * (don't leak that they exist). userId is null for guests.
     */
    public Found visible(String owner, String name, Long userId) {
        Found f = find(owner, name).orElseThrow(() -> ApiException.notFound("Repository"));
        if ("PUBLIC".equals(f.visibility()) || roleOf(f.id(), userId) != null) {
            return f;
        }
        throw ApiException.notFound("Repository");
    }

    /** Owner-only actions: 404 if not visible, 403 if visible but not the owner. */
    public Found asOwner(String owner, String name, long userId) {
        Found f = visible(owner, name, userId);
        if (f.ownerId() != userId) {
            throw ApiException.forbidden("Only the owner can do that");
        }
        return f;
    }

    public RepoView view(Found f, Long userId) {
        return new RepoView(f.id(), f.owner(), f.name(), f.description(), f.visibility(), f.defaultBranch(),
                roleOf(f.id(), userId), f.createdAt(), f.updatedAt());
    }

    @Transactional
    public RepoView create(long userId, String username, String name, String description, String visibility) {
        RepoNames.check(name);
        String vis = parseVisibility(visibility, "PRIVATE");
        String desc = cleanDescription(description);

        Optional<Boolean> existing = jdbc.query(
                "SELECT deleted_at IS NOT NULL FROM repositories WHERE owner_id = ? AND name = ?",
                (rs, i) -> rs.getBoolean(1), userId, name).stream().findFirst();
        if (existing.isPresent()) {
            throw nameTaken(existing.get());
        }

        long repoId;
        try {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO repositories (owner_id, name, description, visibility) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, userId);
                ps.setString(2, name);
                ps.setString(3, desc);
                ps.setString(4, vis);
                return ps;
            }, keys);
            repoId = keys.getKey().longValue();
        } catch (DuplicateKeyException e) {
            throw nameTaken(false);
        }

        try {
            jdbc.update("INSERT INTO repo_permissions (repo_id, user_id, role) VALUES (?, ?, 'OWNER')", repoId, userId);

            // main + an initial empty commit (SRS). The commit time written to the column is the one in the object.
            String emptyTree = stores.buildTree(repoId, Map.of());
            StoredCommit first = stores.writeCommit(repoId, emptyTree, List.of(), username, "Initial commit");
            commitRows.insert(repoId, first.hash(), emptyTree, userId, "Initial commit", first.createdAt(), List.of());
            jdbc.update("INSERT INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (?, 'main', ?, TRUE)",
                    repoId, first.hash());
        } catch (RuntimeException e) {
            deleteDirectory(stores.repoDir(repoId));   // the DB rows roll back; don't leave an orphan folder
            throw e;
        }
        return view(find(username, name).orElseThrow(), userId);
    }

    /** Repos the user owns or collaborates on, newest first. */
    public PageResponse<RepoView> mine(long userId, int page, int size) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM repo_permissions p JOIN repositories r ON r.id = p.repo_id "
                        + "WHERE p.user_id = ? AND r.deleted_at IS NULL", Long.class, userId);
        List<RepoView> items = jdbc.query(SELECT
                        + "JOIN repo_permissions p ON p.repo_id = r.id AND p.user_id = ? "
                        + "WHERE r.deleted_at IS NULL ORDER BY r.updated_at DESC, r.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Found f = map(rs, i);
                    return view(f, userId);
                }, userId, size, PageResponse.offset(page, size));
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    @Transactional
    public RepoView update(Found repo, long userId, String newName, String newDescription, String newVisibility) {
        String name = newName == null ? repo.name() : RepoNames.check(newName);
        String desc = newDescription == null ? repo.description() : cleanDescription(newDescription);
        String vis = parseVisibility(newVisibility, repo.visibility());
        if (!name.equals(repo.name())) {
            // The unique key covers recycle-bin repos too, so a name in the bin is also taken.
            Optional<Boolean> clash = jdbc.query(
                    "SELECT deleted_at IS NOT NULL FROM repositories WHERE owner_id = ? AND name = ?",
                    (rs, i) -> rs.getBoolean(1), repo.ownerId(), name).stream().findFirst();
            if (clash.isPresent()) {
                throw nameTaken(clash.get());
            }
        }
        jdbc.update("UPDATE repositories SET name = ?, description = ?, visibility = ? WHERE id = ?",
                name, desc, vis, repo.id());
        return view(find(repo.owner(), name).orElseThrow(), userId);
    }

    /** Soft delete: hide the repo and put it in the recycle bin for 7 days. */
    @Transactional
    public void delete(Found repo, long userId) {
        Instant now = Instant.now();
        jdbc.update("UPDATE repositories SET deleted_at = ? WHERE id = ?", Timestamp.from(now), repo.id());
        jdbc.update("INSERT INTO recycle_bin (item_type, item_id, repo_id, deleted_by, deleted_at, expires_at) "
                        + "VALUES ('REPOSITORY', ?, ?, ?, ?, ?)",
                repo.id(), repo.id(), userId, Timestamp.from(now), Timestamp.from(now.plus(BIN_RETENTION)));
    }

    private static String parseVisibility(String v, String fallback) {
        if (v == null || v.isBlank()) return fallback;
        String up = v.strip().toUpperCase();
        if (!up.equals("PUBLIC") && !up.equals("PRIVATE")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Visibility must be PUBLIC or PRIVATE");
        }
        return up;
    }

    private static String cleanDescription(String d) {
        if (d == null || d.isBlank()) return null;
        if (d.length() > 500) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Description can be at most 500 characters");
        }
        return d.strip();
    }

    private static ApiException nameTaken(boolean inRecycleBin) {
        return ApiException.conflict("NAME_TAKEN", inRecycleBin
                ? "You have a repository with that name in the recycle bin. Restore or delete it first."
                : "You already have a repository with that name");
    }

    /** Best effort recursive delete of a repository folder (used for failed creates and permanent deletes). */
    public static void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            // Leftover files are harmless garbage; never fail the request over them.
        }
    }
}

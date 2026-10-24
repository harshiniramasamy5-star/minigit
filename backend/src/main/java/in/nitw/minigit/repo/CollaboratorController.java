package in.nitw.minigit.repo;

import in.nitw.minigit.access.RepoAccess;
import in.nitw.minigit.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * U9: who can do what on a repo. Roles are READ / WRITE / OWNER (SRS Table 2). The owner has an OWNER row
 * (written by RepoService.create) and is the only one who can add, change or remove collaborators.
 * The API only hands out READ or WRITE; ownership can't be given away.
 */
@RestController
@RequestMapping("/api/v1/repos/{owner}/{repo}/collaborators")
public class CollaboratorController {

    public record Collaborator(long userId, String username, String role) {}
    public record RoleRequest(String role) {}

    private final RepoService repos;
    private final RepoAccess access;
    private final JdbcTemplate jdbc;

    public CollaboratorController(RepoService repos, RepoAccess access, JdbcTemplate jdbc) {
        this.repos = repos;
        this.access = access;
        this.jdbc = jdbc;
    }

    /** Anyone with a role on the repo can see the list. */
    @GetMapping
    public List<Collaborator> list(@PathVariable String owner, @PathVariable String repo) {
        long userId = access.currentUserId();
        RepoService.Found f = repos.visible(owner, repo, userId);
        if (repos.roleOf(f.id(), userId) == null) {
            throw ApiException.forbidden("Only collaborators can see who has access");
        }
        return jdbc.query("SELECT u.id, u.username, p.role FROM repo_permissions p JOIN users u ON u.id = p.user_id "
                        + "WHERE p.repo_id = ? ORDER BY FIELD(p.role, 'OWNER', 'WRITE', 'READ'), u.username",
                (rs, i) -> new Collaborator(rs.getLong(1), rs.getString(2), rs.getString(3)), f.id());
    }

    @PutMapping("/{username}")
    public Collaborator put(@PathVariable String owner, @PathVariable String repo,
                            @PathVariable String username, @RequestBody RoleRequest req) {
        long userId = access.currentUserId();
        RepoService.Found f = repos.asOwner(owner, repo, userId);
        String role = parseRole(req.role());
        long targetId = jdbc.query("SELECT id FROM users WHERE username = ?", (rs, i) -> rs.getLong(1), username)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("User"));
        if (targetId == f.ownerId()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "The owner's role can't be changed");
        }
        jdbc.update("INSERT INTO repo_permissions (repo_id, user_id, role) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE role = VALUES(role)", f.id(), targetId, role);
        return new Collaborator(targetId, username, role);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String owner, @PathVariable String repo, @PathVariable String username) {
        long userId = access.currentUserId();
        RepoService.Found f = repos.asOwner(owner, repo, userId);
        long targetId = jdbc.query("SELECT id FROM users WHERE username = ?", (rs, i) -> rs.getLong(1), username)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("User"));
        if (targetId == f.ownerId()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "The owner can't be removed");
        }
        if (jdbc.update("DELETE FROM repo_permissions WHERE repo_id = ? AND user_id = ?", f.id(), targetId) == 0) {
            throw ApiException.notFound("Collaborator");
        }
    }

    static String parseRole(String role) {
        String up = role == null ? "" : role.strip().toUpperCase(Locale.ROOT);
        if (!up.equals("READ") && !up.equals("WRITE")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Role must be READ or WRITE");
        }
        return up;
    }
}

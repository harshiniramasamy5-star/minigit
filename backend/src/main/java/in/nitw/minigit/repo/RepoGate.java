package in.nitw.minigit.repo;

import in.nitw.minigit.access.RepoAccess;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.readmodel.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Access check for the {owner}/{repo} endpoints (SRS Table 2).
 * read(): anyone for a public repo (guests too), collaborators for a private one (404 otherwise, no leaking).
 * write(): must be logged in with a WRITE or OWNER role.
 */
@Component
public class RepoGate {

    public record Ctx(RepoService.Found repo, Long userId, String role) {
        public long id() { return repo.id(); }
    }

    private final RepoService repos;
    private final CurrentUser currentUser;
    private final RepoAccess access;
    private final HttpServletRequest request;

    public RepoGate(RepoService repos, CurrentUser currentUser, RepoAccess access, HttpServletRequest request) {
        this.repos = repos;
        this.currentUser = currentUser;
        this.access = access;
        this.request = request;
    }

    public Ctx read(String owner, String repo) {
        Long userId = currentUser.findId(request.getHeader("X-Dev-User")).orElse(null);
        RepoService.Found f = repos.visible(owner, repo, userId);
        return new Ctx(f, userId, repos.roleOf(f.id(), userId));
    }

    public Ctx write(String owner, String repo) {
        long userId = access.currentUserId();
        RepoService.Found f = repos.visible(owner, repo, userId);
        String role = repos.roleOf(f.id(), userId);
        if (!"WRITE".equals(role) && !"OWNER".equals(role)) {
            throw ApiException.forbidden("Write access is needed for that");
        }
        return new Ctx(f, userId, role);
    }

    /** Logged in with any role on the repo (Read, Write or Owner): enough to comment on a pull request. */
    public Ctx member(String owner, String repo) {
        long userId = access.currentUserId();
        RepoService.Found f = repos.visible(owner, repo, userId);
        String role = repos.roleOf(f.id(), userId);
        if (role == null) {
            throw ApiException.forbidden("Only collaborators can do that");
        }
        return new Ctx(f, userId, role);
    }

    public String username(long userId) {
        return access.username(userId);
    }
}

package in.nitw.minigit.access;

import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.readmodel.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Who is calling, and may they touch this repo (SRS Table 2).
 * The user comes from the shared CurrentUser helper, so the X-Dev-User header works in
 * open-dev mode and the JWT takes over once U1 lands.
 */
@Component
public class RepoAccess {

    public enum Level { READ, WRITE, OWNER }

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final HttpServletRequest request;

    public RepoAccess(JdbcTemplate jdbc, CurrentUser currentUser, HttpServletRequest request) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.request = request;
    }

    public long currentUserId() {
        return currentUser.requireId(request.getHeader("X-Dev-User"));
    }

    public String username(long userId) {
        List<String> names = jdbc.queryForList("SELECT username FROM users WHERE id = ?", String.class, userId);
        return names.isEmpty() ? "user-" + userId : names.get(0);
    }

    /** Throws 404 if the repo doesn't exist, 403 if the user's role is below {@code needed}. */
    public void require(long repoId, long userId, Level needed) {
        List<Long> owners = jdbc.queryForList("SELECT owner_id FROM repositories WHERE id = ?", Long.class, repoId);
        if (owners.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Repository not found");
        }
        Level have;
        if (owners.get(0) == userId) {
            have = Level.OWNER;
        } else {
            List<String> roles = jdbc.queryForList(
                    "SELECT role FROM repo_permissions WHERE repo_id = ? AND user_id = ?",
                    String.class, repoId, userId);
            if (roles.isEmpty()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have access to this repository");
            }
            have = Level.valueOf(roles.get(0));
        }
        if (have.ordinal() < needed.ordinal()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", needed + " access needed");
        }
    }
}

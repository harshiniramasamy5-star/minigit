package in.nitw.minigit.access;

import in.nitw.minigit.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Who is calling, and may they touch this repo (SRS Table 2).
 * Shared by the branch, merge and undo controllers. If U1/U9 already ship an
 * equivalent helper, switch these call sites to it and delete this class.
 */
@Component
public class RepoAccess {

    public enum Level { READ, WRITE, OWNER }

    private final JdbcTemplate jdbc;

    public RepoAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Logged-in user id. The JWT subject can be either the numeric id or the username. */
    public long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Log in first");
        }
        String name = auth.getName();
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException notNumeric) {
            List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE username = ?", Long.class, name);
            if (ids.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Unknown user");
            }
            return ids.get(0);
        }
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

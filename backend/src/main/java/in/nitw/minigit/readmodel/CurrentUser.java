package in.nitw.minigit.readmodel;

import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the logged-in user's id.
 *
 * CONTRACT for U1 (Shrejal): the JWT filter must put an authenticated Authentication in the
 * SecurityContext whose getName() is the USERNAME. That's all this class relies on.
 *
 * Until auth exists, in open-dev mode only, an {@code X-Dev-User: <username>} header stands in
 * for the token so the dashboard can be tested with curl. It is ignored when open-dev is off.
 */
@Component
public class CurrentUser {

    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public CurrentUser(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public long requireId(String devUserHeader) {
        return findId(devUserHeader)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in"));
    }

    /** Like {@link #requireId} but empty for guests (public pages that work without logging in). */
    public Optional<Long> findId(String devUserHeader) {
        String username = resolveUsername(devUserHeader);
        if (username == null) {
            return Optional.empty();
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE username = ?", Long.class, username);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private String resolveUsername(String devUserHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        if (props.security().openDev() && devUserHeader != null && !devUserHeader.isBlank()) {
            return devUserHeader.strip();
        }
        return null;
    }
}

package in.nitw.minigit.auth;

import org.springframework.beans.factory.annotation.Autowired;
import in.nitw.minigit.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/** U1: register, login with lockout (5 wrong passwords = 15 min), current user. */
@Service
public class AuthService {

    public static final int MAX_FAILED = 5;
    public static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]{3,40}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public record UserView(long id, String username, String email) {}
    public record LoginResult(String token, Instant expiresAt, UserView user) {}

    private final UserRows users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Clock clock;

    @Autowired
    public AuthService(UserRows users, PasswordEncoder encoder, JwtService jwt) {
        this(users, encoder, jwt, Clock.systemUTC());
    }

    AuthService(UserRows users, PasswordEncoder encoder, JwtService jwt, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.clock = clock;
    }

    public UserView register(String username, String email, String password) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw ApiException.badRequest("VALIDATION_FAILED",
                    "Username must be 3-40 characters: letters, digits, _ or -");
        }
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Enter a valid email address");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            // BCrypt only reads the first 72 bytes, so longer passwords would be silently truncated.
            throw ApiException.badRequest("VALIDATION_FAILED", "Password must be 8-72 characters");
        }
        String cleanEmail = email.strip().toLowerCase(Locale.ROOT);
        if (users.usernameExists(username)) {
            throw ApiException.conflict("NAME_TAKEN", "That username is taken");
        }
        if (users.emailExists(cleanEmail)) {
            throw ApiException.conflict("NAME_TAKEN", "That email is already registered");
        }
        long id = users.insert(username, cleanEmail, encoder.encode(password))
                .orElseThrow(() -> ApiException.conflict("NAME_TAKEN", "That username or email is taken"));
        return new UserView(id, username, cleanEmail);
    }

    public LoginResult login(String usernameOrEmail, String password) {
        String id = usernameOrEmail == null ? "" : usernameOrEmail.strip();
        UserRows.User user = users.findByUsernameOrEmail(id.contains("@") ? id.toLowerCase(Locale.ROOT) : id)
                .orElseThrow(AuthService::badLogin);
        Instant now = clock.instant();

        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw locked(user.lockedUntil(), now);
        }
        if (password == null || !encoder.matches(password, user.passwordHash())) {
            // An expired lock starts a fresh count.
            int failed = (user.lockedUntil() != null ? 0 : user.failedAttempts()) + 1;
            if (failed >= MAX_FAILED) {
                Instant until = now.plus(LOCK_TIME);
                users.saveFailure(user.id(), 0, until);
                throw locked(until, now);
            }
            users.saveFailure(user.id(), failed, null);
            throw badLogin();
        }
        users.recordSuccess(user.id());
        JwtService.Issued issued = jwt.issue(user.id(), user.username());
        return new LoginResult(issued.token(), issued.expiresAt(),
                new UserView(user.id(), user.username(), user.email()));
    }

    public UserView me(String username) {
        UserRows.User u = users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in"));
        return new UserView(u.id(), u.username(), u.email());
    }

    private static ApiException badLogin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Wrong username or password");
    }

    private static ApiException locked(Instant until, Instant now) {
        long mins = Math.max(1, Duration.between(now, until).plusSeconds(59).toMinutes());
        return new ApiException(HttpStatus.valueOf(423), "ACCOUNT_LOCKED",
                "Too many failed logins. Try again in " + mins + " minute" + (mins == 1 ? "" : "s") + ".");
    }
}

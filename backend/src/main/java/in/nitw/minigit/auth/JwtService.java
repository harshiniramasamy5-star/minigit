package in.nitw.minigit.auth;

import org.springframework.beans.factory.annotation.Autowired;
import in.nitw.minigit.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and checks the login token. FIXED expiry (SRS: 30 min from login, no refresh, no sliding window).
 * The subject is the USERNAME, which is what CurrentUser relies on.
 */
@Component
public class JwtService {

    public record Issued(String token, Instant expiresAt) {}

    private final SecretKey key;
    private final Duration lifetime;
    private final Clock clock;

    @Autowired
    public JwtService(AppProperties props) {
        this(props.jwt().secret(), props.jwt().expiryMinutes(), Clock.systemUTC());
    }

    JwtService(String secret, int expiryMinutes, Clock clock) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("minigit.jwt.secret must be at least 32 bytes (set JWT_SECRET)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.lifetime = Duration.ofMinutes(expiryMinutes);
        this.clock = clock;
    }

    public Issued issue(long userId, String username) {
        Instant now = clock.instant();
        Instant exp = now.plus(lifetime);
        String token = Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new Issued(token, exp);
    }

    /** @return the username, or empty if the token is missing, forged or expired. */
    public Optional<String> usernameFrom(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant()))
                    .build().parseSignedClaims(token).getPayload();
            return Optional.ofNullable(c.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

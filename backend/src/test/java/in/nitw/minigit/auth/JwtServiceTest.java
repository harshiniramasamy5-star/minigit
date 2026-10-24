package in.nitw.minigit.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-24T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void tokenCarriesTheUsernameAndExpiresAfterThirtyMinutes() {
        MutableClock clock = new MutableClock();
        JwtService jwt = new JwtService(SECRET, 30, clock);
        JwtService.Issued issued = jwt.issue(7, "asha");
        assertEquals(clock.now.plus(Duration.ofMinutes(30)), issued.expiresAt());
        assertEquals(Optional.of("asha"), jwt.usernameFrom(issued.token()));
    }

    @Test
    void expiryIsFixedNotSliding() {
        MutableClock clock = new MutableClock();
        JwtService jwt = new JwtService(SECRET, 30, clock);
        String token = jwt.issue(7, "asha").token();
        clock.now = clock.now.plus(Duration.ofMinutes(29));
        assertTrue(jwt.usernameFrom(token).isPresent());   // using it does not extend it
        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertTrue(jwt.usernameFrom(token).isEmpty());
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        MutableClock clock = new MutableClock();
        JwtService jwt = new JwtService(SECRET, 30, clock);
        JwtService other = new JwtService("ffffffffffffffffffffffffffffffff", 30, clock);
        String token = jwt.issue(7, "asha").token();
        assertTrue(other.usernameFrom(token).isEmpty());
        assertTrue(jwt.usernameFrom(token.substring(0, token.length() - 2) + "xx").isEmpty());
        assertTrue(jwt.usernameFrom("not-a-token").isEmpty());
        assertTrue(jwt.usernameFrom("").isEmpty());
    }

    @Test
    void shortSecretIsRefused() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 30, new MutableClock()));
    }
}

package in.nitw.minigit.auth;

import in.nitw.minigit.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    /** In-memory stand-in for the users table. */
    static class FakeUsers extends UserRows {
        final Map<String, User> byName = new HashMap<>();
        long next = 1;

        FakeUsers() { super(null); }

        @Override public Optional<User> findByUsernameOrEmail(String v) {
            return byName.values().stream().filter(u -> u.username().equals(v) || u.email().equals(v)).findFirst();
        }
        @Override public Optional<User> findByUsername(String n) { return Optional.ofNullable(byName.get(n)); }
        @Override public boolean usernameExists(String n) { return byName.containsKey(n); }
        @Override public boolean emailExists(String e) { return byName.values().stream().anyMatch(u -> u.email().equals(e)); }
        @Override public Optional<Long> insert(String n, String e, String h) {
            long id = next++;
            byName.put(n, new User(id, n, e, h, 0, null));
            return Optional.of(id);
        }
        @Override public void recordSuccess(long id) { update(id, 0, null); }
        @Override public void saveFailure(long id, int failed, Instant until) { update(id, failed, until); }
        private void update(long id, int failed, Instant until) {
            byName.replaceAll((k, u) -> u.id() == id ? new User(id, u.username(), u.email(), u.passwordHash(), failed, until) : u);
        }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-24T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final FakeUsers users = new FakeUsers();
    private final JwtService jwt = new JwtService("0123456789abcdef0123456789abcdef", 30, clock);
    private final AuthService auth = new AuthService(users, new BCryptPasswordEncoder(4), jwt, clock);

    private void registerAsha() { auth.register("asha", "Asha@Example.com", "correct-horse"); }

    private static ApiException thrown(Runnable r) {
        return assertThrows(ApiException.class, r::run);
    }

    @Test
    void registerHashesThePasswordAndLowercasesTheEmail() {
        AuthService.UserView v = auth.register("asha", "Asha@Example.com", "correct-horse");
        assertEquals("asha@example.com", v.email());
        String stored = users.byName.get("asha").passwordHash();
        assertNotEquals("correct-horse", stored);
        assertTrue(new BCryptPasswordEncoder(4).matches("correct-horse", stored));
    }

    @Test
    void registerRejectsBadInput() {
        assertEquals("VALIDATION_FAILED", thrown(() -> auth.register("a", "a@b.co", "longenough")).getCode());
        assertEquals("VALIDATION_FAILED", thrown(() -> auth.register("bad name", "a@b.co", "longenough")).getCode());
        assertEquals("VALIDATION_FAILED", thrown(() -> auth.register("asha", "not-an-email", "longenough")).getCode());
        assertEquals("VALIDATION_FAILED", thrown(() -> auth.register("asha", "a@b.co", "short")).getCode());
        assertEquals("VALIDATION_FAILED", thrown(() -> auth.register("asha", "a@b.co", "x".repeat(73))).getCode());
    }

    @Test
    void duplicateUsernameOrEmailIs409() {
        registerAsha();
        ApiException sameName = thrown(() -> auth.register("asha", "other@example.com", "correct-horse"));
        assertEquals(409, sameName.getStatus().value());
        assertEquals("NAME_TAKEN", sameName.getCode());
        assertEquals("NAME_TAKEN", thrown(() -> auth.register("other", "ASHA@example.com", "correct-horse")).getCode());
    }

    @Test
    void loginWorksWithUsernameOrEmailAndTokenExpiresInThirtyMinutes() {
        registerAsha();
        AuthService.LoginResult byName = auth.login("asha", "correct-horse");
        AuthService.LoginResult byEmail = auth.login("ASHA@example.com", "correct-horse");
        assertEquals("asha", byName.user().username());
        assertEquals("asha", byEmail.user().username());
        assertEquals(clock.instant().plus(Duration.ofMinutes(30)), byName.expiresAt());
        assertEquals(Optional.of("asha"), jwt.usernameFrom(byName.token()));
    }

    @Test
    void unknownUserAndWrongPasswordLookTheSame() {
        registerAsha();
        ApiException unknown = thrown(() -> auth.login("nobody", "whatever1"));
        ApiException wrong = thrown(() -> auth.login("asha", "wrong-password"));
        assertEquals(401, unknown.getStatus().value());
        assertEquals(unknown.getCode(), wrong.getCode());
        assertEquals(unknown.getMessage(), wrong.getMessage());
    }

    @Test
    void fifthWrongPasswordLocksTheAccountForFifteenMinutes() {
        registerAsha();
        for (int i = 0; i < 4; i++) {
            assertEquals("INVALID_CREDENTIALS", thrown(() -> auth.login("asha", "nope-nope")).getCode());
        }
        ApiException fifth = thrown(() -> auth.login("asha", "nope-nope"));
        assertEquals(423, fifth.getStatus().value());
        assertEquals("ACCOUNT_LOCKED", fifth.getCode());

        // The right password does not get in while locked.
        assertEquals("ACCOUNT_LOCKED", thrown(() -> auth.login("asha", "correct-horse")).getCode());
        clock.now = clock.now.plus(Duration.ofMinutes(14));
        assertEquals("ACCOUNT_LOCKED", thrown(() -> auth.login("asha", "correct-horse")).getCode());

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertEquals("asha", auth.login("asha", "correct-horse").user().username());
    }

    @Test
    void successResetsTheFailureCount() {
        registerAsha();
        for (int i = 0; i < 4; i++) thrown(() -> auth.login("asha", "nope-nope"));
        auth.login("asha", "correct-horse");
        for (int i = 0; i < 4; i++) {
            assertEquals("INVALID_CREDENTIALS", thrown(() -> auth.login("asha", "nope-nope")).getCode());
        }
    }

    @Test
    void failuresAfterAnExpiredLockStartFromZero() {
        registerAsha();
        for (int i = 0; i < 5; i++) thrown(() -> auth.login("asha", "nope-nope"));
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        // The expired lock starts a fresh count: failures 1-4 are plain failures, the fifth locks again.
        for (int i = 0; i < 4; i++) {
            assertEquals("INVALID_CREDENTIALS", thrown(() -> auth.login("asha", "nope-nope")).getCode());
        }
        assertEquals("ACCOUNT_LOCKED", thrown(() -> auth.login("asha", "nope-nope")).getCode());
    }
}

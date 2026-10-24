package in.nitw.minigit.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** The users table (V1). Plain JDBC, like the rest of the backend. */
@Repository
public class UserRows {

    public record User(long id, String username, String email, String passwordHash,
                       int failedAttempts, Instant lockedUntil) {}

    private final JdbcTemplate jdbc;

    public UserRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static User map(ResultSet rs, int row) throws SQLException {
        Timestamp locked = rs.getTimestamp("locked_until");
        return new User(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
                rs.getString("password_hash"), rs.getInt("failed_attempts"),
                locked == null ? null : locked.toInstant());
    }

    private static final String COLS = "id, username, email, password_hash, failed_attempts, locked_until";

    public Optional<User> findByUsernameOrEmail(String value) {
        return jdbc.query("SELECT " + COLS + " FROM users WHERE username = ? OR email = ? LIMIT 1",
                UserRows::map, value, value).stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        return jdbc.query("SELECT " + COLS + " FROM users WHERE username = ?", UserRows::map, username)
                .stream().findFirst();
    }

    /** @return the new id, or empty if the username or email is already taken. */
    public Optional<Long> insert(String username, String email, String passwordHash) {
        try {
            jdbc.update("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)",
                    username, email, passwordHash);
            return Optional.ofNullable(jdbc.queryForObject("SELECT id FROM users WHERE username = ?",
                    Long.class, username));
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    public boolean usernameExists(String username) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM users WHERE username = ?", Boolean.class, username));
    }

    public boolean emailExists(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM users WHERE email = ?", Boolean.class, email));
    }

    public void recordSuccess(long id) {
        jdbc.update("UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?", id);
    }

    public void saveFailure(long id, int failedAttempts, Instant lockedUntil) {
        jdbc.update("UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?",
                failedAttempts, lockedUntil == null ? null : Timestamp.from(lockedUntil), id);
    }
}

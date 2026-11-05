package in.nitw.minigit.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

/**
 * What `minigit login` saves in ~/.minigit/credentials: server, token, when the token expires, who it is for.
 * The token is a bearer JWT with a FIXED 30 minute life (no refresh), so after that the user logs in again.
 * The file is created readable by the owner only (on systems that support POSIX permissions).
 */
public record Credentials(String server, String token, Instant expiresAt, String username) {

    /** Read at call time, not cached, so tests can point user.home at a temp folder. */
    public static Path file() {
        return Path.of(System.getProperty("user.home"), ".minigit", "credentials");
    }

    public boolean isExpired() {
        return expiresAt != null && !Instant.now().isBefore(expiresAt);
    }

    public static Optional<Credentials> load() {
        Path f = file();
        if (!Files.isRegularFile(f)) return Optional.empty();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            throw new CliException("Can't read " + f + ": " + e.getMessage());
        }
        String server = p.getProperty("server"), token = p.getProperty("token");
        if (server == null || token == null) return Optional.empty();
        String exp = p.getProperty("expiresAt");
        return Optional.of(new Credentials(server, token, exp == null ? null : Instant.parse(exp),
                p.getProperty("username")));
    }

    public void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Files.deleteIfExists(f);
            try {
                Files.createFile(f, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException notPosix) {
                Files.createFile(f);
            }
            Properties p = new Properties();
            p.setProperty("server", server);
            p.setProperty("token", token);
            if (expiresAt != null) p.setProperty("expiresAt", expiresAt.toString());
            if (username != null) p.setProperty("username", username);
            try (OutputStream out = Files.newOutputStream(f)) {
                p.store(out, "MiniGit credentials. Do not share this file.");
            }
        } catch (IOException e) {
            throw new CliException("Can't save " + f + ": " + e.getMessage());
        }
    }

    /** @return true if a credentials file existed */
    public static boolean delete() {
        try {
            return Files.deleteIfExists(file());
        } catch (IOException e) {
            throw new CliException("Can't delete " + file() + ": " + e.getMessage());
        }
    }
}

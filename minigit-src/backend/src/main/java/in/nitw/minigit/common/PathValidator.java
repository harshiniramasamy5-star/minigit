package in.nitw.minigit.common;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Validates repository-relative file paths coming from the client (uploads, restore, diff).
 * Blocks traversal (".."), absolute paths, Windows drive letters, backslashes, control chars
 * and the reserved ".minigit" directory. Returns a normalised path with forward slashes.
 *
 * Use it before touching the file store:  Path safe = PathValidator.resolveInside(repoRoot, userPath);
 */
public final class PathValidator {

    public static final int MAX_PATH_LENGTH = 1000;   // matches merge_conflicts.path; confirm with V-limits
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");
    private static final String RESERVED = ".minigit";

    private PathValidator() {}

    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPathException("Path is empty");
        }
        if (raw.length() > MAX_PATH_LENGTH) {
            throw new InvalidPathException("Path is longer than " + MAX_PATH_LENGTH + " characters");
        }
        if (CONTROL.matcher(raw).find()) {
            throw new InvalidPathException("Path contains control characters");
        }
        if (raw.contains("\\")) {
            throw new InvalidPathException("Use forward slashes in paths");
        }
        if (raw.startsWith("/") || DRIVE.matcher(raw).matches()) {
            throw new InvalidPathException("Path must be relative to the repository root");
        }

        StringBuilder out = new StringBuilder();
        for (String part : raw.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;                       // tolerate "a//b" and "./a"
            }
            if (part.equals("..")) {
                throw new InvalidPathException("Path cannot contain '..'");
            }
            if (out.length() == 0 && part.equalsIgnoreCase(RESERVED)) {
                throw new InvalidPathException("'" + RESERVED + "' is reserved");
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(part);
        }
        if (out.length() == 0) {
            throw new InvalidPathException("Path is empty");
        }
        return out.toString();
    }

    /** Second line of defence: the resolved path must still sit inside root. */
    public static Path resolveInside(Path root, String raw) {
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(normalise(raw)).normalize();
        if (!resolved.startsWith(base)) {
            throw new InvalidPathException("Path escapes the repository");
        }
        return resolved;
    }

    /** Mapped to 400 INVALID_PATH by GlobalExceptionHandler. */
    public static class InvalidPathException extends IllegalArgumentException {
        public InvalidPathException(String msg) { super(msg); }
    }
}

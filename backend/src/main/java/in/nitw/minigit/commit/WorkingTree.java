package in.nitw.minigit.commit;

import in.nitw.minigit.common.PathValidator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Plain file operations on one user's working copy folder (U3), and the comparison against a commit.
 * No Spring and no database in here, so it can be tested on a temp folder.
 */
public final class WorkingTree {

    public enum Status { ADDED, MODIFIED, DELETED }

    public record Change(String path, Status status) {}

    public record FileInfo(String path, long size) {}

    private WorkingTree() {}

    /** Every regular file under dir as normalised path -> bytes. Missing folder = empty. */
    public static Map<String, byte[]> read(Path dir) {
        Map<String, byte[]> files = new TreeMap<>();
        Path root = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return files;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the working copy", e);
        }
        return files;
    }

    public static List<FileInfo> list(Map<String, byte[]> files) {
        List<FileInfo> out = new ArrayList<>();
        files.forEach((path, bytes) -> out.add(new FileInfo(path, bytes.length)));
        return out;
    }

    /** What differs between a commit's files (base) and the working copy, sorted by path. */
    public static List<Change> changes(Map<String, byte[]> base, Map<String, byte[]> work) {
        Set<String> paths = new TreeSet<>(base.keySet());
        paths.addAll(work.keySet());
        List<Change> out = new ArrayList<>();
        for (String path : paths) {
            byte[] before = base.get(path);
            byte[] after = work.get(path);
            if (before == null) out.add(new Change(path, Status.ADDED));
            else if (after == null) out.add(new Change(path, Status.DELETED));
            else if (!Arrays.equals(before, after)) out.add(new Change(path, Status.MODIFIED));
        }
        return out;
    }

    /**
     * Writes one file. The path is normalised and checked to stay inside dir (PathValidator).
     * @return the normalised path
     * @throws PathValidator.InvalidPathException for bad paths and for file/folder clashes ("a" vs "a/b")
     */
    public static String write(Path dir, String rawPath, byte[] data) {
        String clean = PathValidator.normalise(rawPath);
        Path target = PathValidator.resolveInside(dir.toAbsolutePath().normalize(), clean);
        try {
            if (Files.isDirectory(target)) {
                throw new PathValidator.InvalidPathException("A folder with that name already exists: " + clean);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (java.nio.file.FileSystemException clash) {
            // createDirectories fails when a parent is a regular file
            throw new PathValidator.InvalidPathException("Path conflicts with an existing file: " + clean);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + clean, e);
        }
        return clean;
    }

    /** @return the bytes, or null if there is no such file */
    public static byte[] readFile(Path dir, String rawPath) {
        String clean = PathValidator.normalise(rawPath);
        Path target = PathValidator.resolveInside(dir.toAbsolutePath().normalize(), clean);
        try {
            return Files.isRegularFile(target) ? Files.readAllBytes(target) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + clean, e);
        }
    }

    /** @return true if a file was removed. Empty parent folders are cleaned up. */
    public static boolean delete(Path dir, String rawPath) {
        String clean = PathValidator.normalise(rawPath);
        Path root = dir.toAbsolutePath().normalize();
        Path target = PathValidator.resolveInside(root, clean);
        try {
            if (!Files.isRegularFile(target)) return false;
            Files.delete(target);
            Path parent = target.getParent();
            while (parent != null && !parent.equals(root) && parent.startsWith(root)) {
                try (Stream<Path> kids = Files.list(parent)) {
                    if (kids.findAny().isPresent()) break;
                }
                Files.delete(parent);
                parent = parent.getParent();
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + clean, e);
        }
    }

    /** Deletes the whole folder content (used before re-syncing). Not the folder itself. */
    static void clear(Path dir) {
        Path root = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(root)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

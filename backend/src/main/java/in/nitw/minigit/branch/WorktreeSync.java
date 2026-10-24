package in.nitw.minigit.branch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Makes a working copy match a snapshot exactly: writes every file in the snapshot and
 * deletes files the snapshot doesn't have (HistoryService.restore only does the first half,
 * so switching branches used to leave stale files behind).
 */
public final class WorktreeSync {

    private WorktreeSync() {}

    public static void sync(Map<String, byte[]> snapshot, Path dir) {
        Path root = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            for (Map.Entry<String, byte[]> e : snapshot.entrySet()) {
                Path file = root.resolve(e.getKey()).normalize();
                if (!file.startsWith(root) || file.equals(root)) {
                    throw new SecurityException("Path escapes working copy: " + e.getKey());
                }
                Files.createDirectories(file.getParent());
                Files.write(file, e.getValue());
            }
            List<Path> all;
            try (Stream<Path> s = Files.walk(root)) {
                all = new ArrayList<>(s.toList());
            }
            all.sort(Comparator.reverseOrder()); // children before parents
            for (Path p : all) {
                if (p.equals(root)) continue;
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (Files.isRegularFile(p) && !snapshot.containsKey(rel)) {
                    Files.delete(p);
                } else if (Files.isDirectory(p)) {
                    try (Stream<Path> kids = Files.list(p)) {
                        if (kids.findAny().isEmpty()) Files.delete(p);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update working copy", e);
        }
    }
}

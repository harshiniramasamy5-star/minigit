package in.nitw.minigit.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * What changed in the working folder compared with .minigit/index (the hashes clone, pull and push wrote).
 * Also keeps the pending local commit messages in .minigit/pending (one line per `minigit commit`).
 */
public final class LocalChanges {

    /** status: ADDED | MODIFIED | DELETED. content is null for DELETED. */
    public record Change(String path, String status, byte[] content) {}

    private LocalChanges() {}

    /** Every regular file in the folder (not .minigit, not symlinks), keyed by repo path with '/' separators. */
    public static Map<String, byte[]> workingFiles(RepoConfig cfg) {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(cfg.root)) {
            for (Path p : walk.filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)).toList()) {
                String name = cfg.root.relativize(p).toString().replace(File.separatorChar, '/');
                if (name.equals(RepoConfig.DIR) || name.startsWith(RepoConfig.DIR + "/")) continue;
                cfg.resolveSafe(name); // refuses names the server would reject anyway
                files.put(name, Files.readAllBytes(p));
            }
        } catch (IOException e) {
            throw new CliException("Can't read the folder " + cfg.root + ": " + e.getMessage());
        }
        return files;
    }

    public static List<Change> detect(RepoConfig cfg) {
        Map<String, String> index = cfg.readIndex();
        Map<String, byte[]> working = workingFiles(cfg);
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : working.entrySet()) {
            String known = index.get(e.getKey());
            if (known == null) {
                changes.add(new Change(e.getKey(), "ADDED", e.getValue()));
            } else if (!known.equals(RepoConfig.sha256(e.getValue()))) {
                changes.add(new Change(e.getKey(), "MODIFIED", e.getValue()));
            }
        }
        for (String path : index.keySet()) {
            if (!working.containsKey(path)) changes.add(new Change(path, "DELETED", null));
        }
        changes.sort((a, b) -> a.path().compareTo(b.path()));
        return changes;
    }

    /** After a successful push: the working folder is now what the server has. */
    public static void markSynced(RepoConfig cfg) {
        Map<String, String> index = new TreeMap<>();
        workingFiles(cfg).forEach((path, data) -> index.put(path, RepoConfig.sha256(data)));
        cfg.writeIndex(index);
    }

    // ---- pending commit messages ----

    private static Path pendingFile(RepoConfig cfg) {
        return cfg.root.resolve(RepoConfig.DIR).resolve("pending");
    }

    public static List<String> pendingMessages(RepoConfig cfg) {
        Path f = pendingFile(cfg);
        if (!Files.isRegularFile(f)) return List.of();
        try {
            return Files.readAllLines(f, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new CliException("Can't read .minigit/pending: " + e.getMessage());
        }
    }

    public static void addPending(RepoConfig cfg, String message) {
        List<String> all = new ArrayList<>(pendingMessages(cfg));
        all.add(message.replace('\n', ' ').replace('\r', ' ').strip());
        try {
            Files.writeString(pendingFile(cfg), String.join("\n", all) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CliException("Can't write .minigit/pending: " + e.getMessage());
        }
    }

    public static void clearPending(RepoConfig cfg) {
        try {
            Files.deleteIfExists(pendingFile(cfg));
        } catch (IOException e) {
            throw new CliException("Can't delete .minigit/pending: " + e.getMessage());
        }
    }
}

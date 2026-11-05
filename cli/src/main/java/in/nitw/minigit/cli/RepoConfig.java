package in.nitw.minigit.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * A cloned folder's `.minigit/` data, shared by clone, pull and (for Shrejal) status / commit / push.
 *
 *   .minigit/config   key=value lines: server, owner, repo, branch, base
 *                     (base = the commit the files were last synced with; BRANCH_HEAD_MOVED means the server moved past it)
 *   .minigit/index    one line per tracked file: sha256-of-bytes TAB path  (what we last wrote). A file whose
 *                     current hash differs from its index hash has local changes; that is how `status` and `pull` tell.
 */
public final class RepoConfig {

    public static final String DIR = ".minigit";

    public final Path root;
    public final String server, owner, repo;
    public String branch, base;

    public RepoConfig(Path root, String server, String owner, String repo, String branch, String base) {
        this.root = root.toAbsolutePath().normalize();
        this.server = server;
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
        this.base = base;
    }

    /** Walks up from {@code start} to the folder holding .minigit/config. */
    public static RepoConfig find(Path start) {
        for (Path p = start.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            Path cfg = p.resolve(DIR).resolve("config");
            if (Files.isRegularFile(cfg)) return load(p);
        }
        throw new CliException("This folder is not a MiniGit repository (no .minigit folder found). Use `minigit clone` first.");
    }

    private static RepoConfig load(Path root) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(root.resolve(DIR).resolve("config"))) {
            p.load(in);
        } catch (IOException e) {
            throw new CliException("Can't read .minigit/config: " + e.getMessage());
        }
        for (String k : List.of("server", "owner", "repo", "branch", "base")) {
            if (p.getProperty(k) == null) throw new CliException(".minigit/config is missing '" + k + "'. Clone again.");
        }
        return new RepoConfig(root, p.getProperty("server"), p.getProperty("owner"), p.getProperty("repo"),
                p.getProperty("branch"), p.getProperty("base"));
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("server", server);
        p.setProperty("owner", owner);
        p.setProperty("repo", repo);
        p.setProperty("branch", branch);
        p.setProperty("base", base);
        try {
            Files.createDirectories(root.resolve(DIR));
            try (OutputStream out = Files.newOutputStream(root.resolve(DIR).resolve("config"))) {
                p.store(out, "MiniGit repository config");
            }
        } catch (IOException e) {
            throw new CliException("Can't write .minigit/config: " + e.getMessage());
        }
    }

    public Map<String, String> readIndex() {
        Map<String, String> index = new TreeMap<>();
        Path f = root.resolve(DIR).resolve("index");
        if (!Files.isRegularFile(f)) return index;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0) index.put(line.substring(tab + 1), line.substring(0, tab));
            }
        } catch (IOException e) {
            throw new CliException("Can't read .minigit/index: " + e.getMessage());
        }
        return index;
    }

    public void writeIndex(Map<String, String> index) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(index).forEach((path, sha) -> sb.append(sha).append('\t').append(path).append('\n'));
        try {
            Files.createDirectories(root.resolve(DIR));
            Files.writeString(root.resolve(DIR).resolve("index"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CliException("Can't write .minigit/index: " + e.getMessage());
        }
    }

    /**
     * Maps a repo path like "src/Main.java" to a file under root, refusing anything that could escape it
     * (absolute paths, "..", backslashes, empty segments, or anything inside .minigit).
     */
    public Path resolveSafe(String repoPath) {
        if (repoPath == null || repoPath.isEmpty() || repoPath.startsWith("/") || repoPath.contains("\\")
                || repoPath.contains("\t") || repoPath.contains("\n")) {
            throw new CliException("The server sent an unsafe file path: " + repoPath);
        }
        String[] parts = repoPath.split("/", -1);
        for (String s : parts) {
            if (s.isEmpty() || s.equals(".") || s.equals("..")) {
                throw new CliException("The server sent an unsafe file path: " + repoPath);
            }
        }
        if (parts[0].equals(DIR)) throw new CliException("The server sent a path inside " + DIR + ": " + repoPath);
        Path p = root.resolve(repoPath).normalize();
        if (!p.startsWith(root)) throw new CliException("The server sent an unsafe file path: " + repoPath);
        return p;
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

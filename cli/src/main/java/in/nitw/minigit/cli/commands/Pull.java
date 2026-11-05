package in.nitw.minigit.cli.commands;

import in.nitw.minigit.cli.ApiClient;
import in.nitw.minigit.cli.CliException;
import in.nitw.minigit.cli.ReadApi;
import in.nitw.minigit.cli.RepoConfig;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * minigit pull: brings the branch's newest files into the cloned folder.
 * Asks the server which files changed between the last synced commit (config `base`) and the branch head, and
 * fetches only those. It never overwrites or deletes a file you changed locally: such files are listed as
 * "kept" and pull exits with code 1. Everything else is updated and `base` moves to the new head.
 */
public final class Pull {

    private final PrintStream out;
    private final Path start;

    public Pull() {
        this(System.out, Path.of(""));
    }

    public Pull(PrintStream out, Path startDir) {
        this.out = out;
        this.start = startDir;
    }

    public int run(String... args) {
        if (args.length > 0) throw new CliException("Usage: minigit pull (run it inside a cloned folder)");
        RepoConfig cfg = RepoConfig.find(start);
        ApiClient client = ApiClient.fromCredentials();
        if (!sameServer(client.baseUrl(), cfg.server)) {
            throw new CliException("You are logged in to " + client.baseUrl() + " but this repository was cloned from "
                    + cfg.server + ". Run `minigit login --server " + cfg.server + "`.");
        }
        ReadApi read = new ReadApi(client);
        ReadApi.Branch branch = read.branches(cfg.owner, cfg.repo).stream()
                .filter(b -> b.name().equals(cfg.branch)).findFirst()
                .orElseThrow(() -> new CliException("The branch '" + cfg.branch + "' no longer exists on the server."));
        if (branch.head().equals(cfg.base)) {
            out.println("Already up to date.");
            return 0;
        }

        Map<String, String> index = cfg.readIndex();
        List<String> kept = new ArrayList<>();
        int updated = 0, removed = 0;
        for (ReadApi.Change ch : read.diff(cfg.owner, cfg.repo, cfg.base, branch.head())) {
            Path file = cfg.resolveSafe(ch.path());
            String local = Files.isRegularFile(file) ? hashOf(file) : null;
            String known = index.get(ch.path());
            boolean untouched = local != null && local.equals(known);

            if (ch.status().equals("DELETED")) {
                if (local == null) {
                    index.remove(ch.path());
                } else if (untouched) {
                    deleteFile(file, cfg.root);
                    index.remove(ch.path());
                    removed++;
                } else {
                    kept.add(ch.path() + " (deleted upstream, but you changed it)");
                }
                continue;
            }
            byte[] data = read.blob(cfg.owner, cfg.repo, branch.head(), ch.path());
            String remote = RepoConfig.sha256(data);
            if (remote.equals(local)) {                       // already identical
                index.put(ch.path(), remote);
            } else if (untouched || (local == null && known == null)) {
                write(file, data);                            // safe: unmodified locally, or a brand new file
                index.put(ch.path(), remote);
                updated++;
            } else {
                kept.add(ch.path() + (local == null ? " (you deleted it locally)" : " (you changed it locally)"));
            }
        }
        cfg.writeIndex(index);
        cfg.base = branch.head();
        cfg.save();

        out.println("Pulled " + cfg.branch + " to " + branch.head().substring(0, 7) + ": " + updated + " updated, "
                + removed + " removed.");
        if (!kept.isEmpty()) {
            out.println("These files have newer versions on the server but were left alone:");
            kept.forEach(k -> out.println("  " + k));
            out.println("Copy your changes somewhere, delete the file, and pull again to get the server version.");
            return 1;
        }
        return 0;
    }

    private static boolean sameServer(String a, String b) {
        return a.replaceAll("/+$", "").equalsIgnoreCase(b.replaceAll("/+$", ""));
    }

    private static String hashOf(Path f) {
        try {
            return RepoConfig.sha256(Files.readAllBytes(f));
        } catch (IOException e) {
            throw new CliException("Can't read " + f + ": " + e.getMessage());
        }
    }

    private static void write(Path f, byte[] data) {
        try {
            Files.createDirectories(f.getParent());
            Files.write(f, data);
        } catch (IOException e) {
            throw new CliException("Can't write " + f + ": " + e.getMessage());
        }
    }

    /** Deletes a file, then any folders it leaves empty (never the repo root). */
    private static void deleteFile(Path f, Path root) {
        try {
            Files.deleteIfExists(f);
            for (Path d = f.getParent(); d != null && !d.equals(root) && d.startsWith(root); d = d.getParent()) {
                try (DirectoryStream<Path> s = Files.newDirectoryStream(d)) {
                    if (s.iterator().hasNext()) break;
                }
                Files.delete(d);
            }
        } catch (IOException e) {
            throw new CliException("Can't delete " + f + ": " + e.getMessage());
        }
    }
}

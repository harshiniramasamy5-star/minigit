package in.nitw.minigit.cli.commands;

import in.nitw.minigit.cli.CliException;
import in.nitw.minigit.cli.LocalChanges;
import in.nitw.minigit.cli.RepoConfig;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * minigit commit -m "message": records the message locally. Nothing is sent until `minigit push`, which uploads
 * the files that differ from the last sync (so edits made after `commit` are included) as one server commit.
 */
public final class Commit {

    private final PrintStream out;
    private final Path start;

    public Commit() {
        this(System.out, Path.of(""));
    }

    public Commit(PrintStream out, Path startDir) {
        this.out = out;
        this.start = startDir;
    }

    public int run(String... args) {
        String message = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-m") || args[i].equals("--message")) {
                if (i + 1 >= args.length) throw usage();
                message = args[++i];
            } else {
                throw usage();
            }
        }
        if (message == null || message.isBlank()) throw usage();

        RepoConfig cfg = RepoConfig.find(start);
        List<LocalChanges.Change> changes = LocalChanges.detect(cfg);
        if (changes.isEmpty()) throw new CliException("Nothing to commit: the folder matches the server version.");
        LocalChanges.addPending(cfg, message);
        out.println("[" + cfg.branch + "] " + message.strip());
        out.println(changes.size() + " file" + (changes.size() == 1 ? "" : "s") + " changed. Run `minigit push` to upload.");
        return 0;
    }

    private static CliException usage() {
        return new CliException("Usage: minigit commit -m \"message\"");
    }
}

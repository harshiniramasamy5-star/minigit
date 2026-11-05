package in.nitw.minigit.cli.commands;

import in.nitw.minigit.cli.CliException;
import in.nitw.minigit.cli.LocalChanges;
import in.nitw.minigit.cli.RepoConfig;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/** minigit status: what changed in this folder since the last clone, pull or push. Nothing is sent to the server. */
public final class Status {

    private final PrintStream out;
    private final Path start;

    public Status() {
        this(System.out, Path.of(""));
    }

    public Status(PrintStream out, Path startDir) {
        this.out = out;
        this.start = startDir;
    }

    public int run(String... args) {
        if (args.length > 0) throw new CliException("Usage: minigit status");
        RepoConfig cfg = RepoConfig.find(start);
        List<LocalChanges.Change> changes = LocalChanges.detect(cfg);
        List<String> pending = LocalChanges.pendingMessages(cfg);

        out.println("On branch " + cfg.branch + " (" + cfg.owner + "/" + cfg.repo + ")");
        out.println("Based on commit " + cfg.base.substring(0, Math.min(7, cfg.base.length())));
        if (!pending.isEmpty()) {
            out.println(pending.size() + " local commit" + (pending.size() == 1 ? "" : "s") + " not pushed yet:");
            pending.forEach(m -> out.println("  " + m));
        }
        if (changes.isEmpty()) {
            out.println("Working folder clean.");
            return 0;
        }
        out.println();
        for (String status : List.of("ADDED", "MODIFIED", "DELETED")) {
            List<LocalChanges.Change> group = changes.stream().filter(c -> c.status().equals(status)).toList();
            if (group.isEmpty()) continue;
            out.println(status.charAt(0) + status.substring(1).toLowerCase() + ":");
            group.forEach(c -> out.println("    " + c.path()));
        }
        return 0;
    }
}

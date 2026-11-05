package in.nitw.minigit.cli.commands;

import in.nitw.minigit.cli.CliException;
import in.nitw.minigit.cli.LocalChanges;
import in.nitw.minigit.cli.PushApi;
import in.nitw.minigit.cli.RepoConfig;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/** minigit push [--new-branch name]: uploads the changed files and creates ONE commit on the server. */
public final class Push {

    private final PrintStream out;
    private final Path start;

    public Push() {
        this(System.out, Path.of(""));
    }

    public Push(PrintStream out, Path startDir) {
        this.out = out;
        this.start = startDir;
    }

    public int run(String... args) {
        String newBranch = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--new-branch")) {
                if (i + 1 >= args.length) throw new CliException("--new-branch needs a branch name");
                newBranch = args[++i];
            } else {
                throw new CliException("Usage: minigit push [--new-branch name]");
            }
        }
        RepoConfig cfg = RepoConfig.find(start);
        List<String> pending = LocalChanges.pendingMessages(cfg);
        if (pending.isEmpty()) throw new CliException("No commit to push. Run: minigit commit -m \"message\"");
        List<LocalChanges.Change> changes = LocalChanges.detect(cfg);
        if (changes.isEmpty()) throw new CliException("Nothing to push: the folder matches the server version.");

        // Several local commits become one server commit; the message lists all of them.
        String message = String.join("\n", pending);
        String hash = new PushApi().push(cfg, message, changes, newBranch);

        if (newBranch != null) cfg.branch = newBranch;
        cfg.base = hash;
        LocalChanges.markSynced(cfg);
        cfg.save();
        LocalChanges.clearPending(cfg);

        out.println("[" + cfg.branch + " " + hash.substring(0, Math.min(7, hash.length())) + "] "
                + pending.get(pending.size() - 1));
        out.println(changes.size() + " file" + (changes.size() == 1 ? "" : "s") + " pushed.");
        return 0;
    }
}

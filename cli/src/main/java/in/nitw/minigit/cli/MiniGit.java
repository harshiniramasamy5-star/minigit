package in.nitw.minigit.cli;

import in.nitw.minigit.cli.commands.Clone;
import in.nitw.minigit.cli.commands.Commit;
import in.nitw.minigit.cli.commands.Login;
import in.nitw.minigit.cli.commands.Logout;
import in.nitw.minigit.cli.commands.Pull;
import in.nitw.minigit.cli.commands.Push;
import in.nitw.minigit.cli.commands.Status;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;

/** Entry point: `minigit <command> [args]`. Every command is a plain class with run(String...) returning the exit code. */
public final class MiniGit {

    private static final String HELP = """
            MiniGit command-line client

            Usage: minigit <command> [options]

              login [--server URL] [user]    log in (valid for 30 minutes)
              logout                         forget the saved login
              clone owner/repo [dir] [-b branch]
              status                         show changes in this folder
              commit -m "message"            record a local commit
              push [--new-branch name]       upload local commits as one server commit
              pull                           update this folder from the server
            """;

    private MiniGit() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h")) {
            out.print(HELP);
            return args.length == 0 ? 1 : 0;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            return switch (args[0]) {
                case "login" -> new Login(out).run(rest);
                case "logout" -> new Logout(out).run(rest);
                case "clone" -> new Clone(out).run(rest);
                case "status" -> new Status(out, Path.of("")).run(rest);
                case "commit" -> new Commit(out, Path.of("")).run(rest);
                case "push" -> new Push(out, Path.of("")).run(rest);
                case "pull" -> new Pull(out, Path.of("")).run(rest);
                default -> throw new CliException("Unknown command '" + args[0] + "'. Run: minigit help");
            };
        } catch (CliException e) {
            err.println("Error: " + e.getMessage());
            return e.exitCode();
        }
    }
}

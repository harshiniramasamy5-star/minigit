package in.nitw.minigit.cli;

/** An error meant for the person at the terminal: print {@link #getMessage()} and exit with {@link #exitCode()}. No stack trace. */
public class CliException extends RuntimeException {
    private final int exitCode;

    public CliException(String message) {
        this(message, 1);
    }

    public CliException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}

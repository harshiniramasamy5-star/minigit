package in.nitw.minigit.storage;

/** One line of the commit history: the commit's hash plus the Commit itself. */
public record LogEntry(String hash, Commit commit) {}
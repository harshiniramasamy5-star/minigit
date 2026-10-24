package in.nitw.minigit.storage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Commit extends StorageObject {

    private final String treeHash;
    private final List<String> parentHashes; // 0 = first commit, 1 = normal, 2 = merge
    private final String author;
    private final Instant time;
    private final String message;

    public Commit(String treeHash, List<String> parentHashes, String author,
                  Instant time, String message) {
        checkHash(treeHash);
        for (String p : parentHashes) checkHash(p);
        if (author == null || author.isBlank() || author.contains("\n")) {
            throw new IllegalArgumentException("Invalid author");
        }
        this.treeHash = treeHash;
        this.parentHashes = List.copyOf(parentHashes);
        this.author = author;
        this.time = Instant.ofEpochMilli(time.toEpochMilli()); // millisecond precision
        this.message = message == null ? "" : message;
    }

    public String getTreeHash() { return treeHash; }
    public List<String> getParentHashes() { return parentHashes; }
    public String getAuthor() { return author; }
    public Instant getTime() { return time; }
    public String getMessage() { return message; }

    @Override
    public String getType() {
        return "commit";
    }

    @Override
    protected byte[] serializeBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("tree ").append(treeHash).append('\n');
        for (String p : parentHashes) {
            sb.append("parent ").append(p).append('\n');
        }
        sb.append("author ").append(author).append('\n');
        sb.append("time ").append(time.toEpochMilli()).append('\n');
        sb.append('\n').append(message); // blank line, then the message
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Rebuilds a Commit from the body bytes (used by ObjectStore.read). */
    public static Commit fromBody(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        int split = text.indexOf("\n\n");
        if (split < 0) {
            throw new IllegalArgumentException("Corrupt commit");
        }
        String headers = text.substring(0, split);
        String message = text.substring(split + 2);

        String tree = null;
        String author = null;
        Instant time = null;
        List<String> parents = new ArrayList<>();

        for (String line : headers.split("\n")) {
            int sp = line.indexOf(' ');
            String key = line.substring(0, sp);
            String value = line.substring(sp + 1);
            switch (key) {
                case "tree" -> tree = value;
                case "parent" -> parents.add(value);
                case "author" -> author = value;
                case "time" -> time = Instant.ofEpochMilli(Long.parseLong(value));
                default -> throw new IllegalArgumentException("Unknown commit field: " + key);
            }
        }
        return new Commit(tree, parents, author, time, message);
    }

    private static void checkHash(String h) {
        if (h == null || !h.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid hash: " + h);
        }
    }
}
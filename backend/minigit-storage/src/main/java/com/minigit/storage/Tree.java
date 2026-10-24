package com.minigit.storage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Tree extends StorageObject {
    private final List<TreeEntry> entries;

    public Tree(List<TreeEntry> entries) {
        List<TreeEntry> sorted = new ArrayList<>(entries);
        for (TreeEntry e : sorted) {
            validateName(e.name());
        }
        sorted.sort(Comparator.comparing(TreeEntry::name));
        this.entries = List.copyOf(sorted);
    }

    public List<TreeEntry> getEntries() {
        return entries;
    }

    @Override
    public String getType() {
        return "tree";
    }

    /** One line per entry: "type hash name\n" */
    @Override
    protected byte[] serializeBody() {
        StringBuilder sb = new StringBuilder();
        for (TreeEntry e : entries) {
            sb.append(e.type()).append(' ')
                    .append(e.hash()).append(' ')
                    .append(e.name()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Rebuilds a Tree from the body bytes (used later when reading from disk). */
    public static Tree fromBody(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        List<TreeEntry> list = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split(" ", 3);
            list.add(new TreeEntry(parts[0], parts[2], parts[1]));
        }
        return new Tree(list);
    }

    private static void validateName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\n") || name.contains("\0")) {
            throw new IllegalArgumentException("Invalid name in tree: " + name);
        }
    }

    public byte[] serializeBodyForTest() {
        return serializeBody();
    }

}

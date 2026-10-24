package com.minigit.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TreeBuilder {

    private final ObjectStore store;

    public TreeBuilder(ObjectStore store) {
        this.store = store;
    }

    /** Folder in memory while we are building. */
    private static class Node {
        final Map<String, String> files = new TreeMap<>(); // name -> blob hash
        final Map<String, Node> dirs = new TreeMap<>();    // name -> subfolder
    }

    /**
     * @param files path -> content, e.g. "src/App.java" -> bytes
     * @return hash of the root tree
     */
    public String build(Map<String, byte[]> files) {
        Node root = new Node();

        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            String[] parts = splitPath(f.getKey());

            Node current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                String dir = parts[i];
                if (current.files.containsKey(dir)) {
                    throw new IllegalArgumentException("Path conflict: " + f.getKey());
                }
                current = current.dirs.computeIfAbsent(dir, k -> new Node());
            }

            String fileName = parts[parts.length - 1];
            if (current.dirs.containsKey(fileName)) {
                throw new IllegalArgumentException("Path conflict: " + f.getKey());
            }
            current.files.put(fileName, store.write(new Blob(f.getValue())));
        }

        return writeNode(root);
    }

    /** Writes children first, then this folder (bottom-up). */
    private String writeNode(Node node) {
        List<TreeEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> f : node.files.entrySet()) {
            entries.add(new TreeEntry("blob", f.getKey(), f.getValue()));
        }
        for (Map.Entry<String, Node> d : node.dirs.entrySet()) {
            entries.add(new TreeEntry("tree", d.getKey(), writeNode(d.getValue())));
        }
        return store.write(new Tree(entries));
    }

    /** Blocks "../", absolute paths, empty segments. */
    private static String[] splitPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Empty path");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Absolute path not allowed: " + path);
        }
        String[] parts = normalized.split("/", -1);
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".") || p.equals("..")) {
                throw new IllegalArgumentException("Illegal path: " + path);
            }
        }
        return parts;
    }
}
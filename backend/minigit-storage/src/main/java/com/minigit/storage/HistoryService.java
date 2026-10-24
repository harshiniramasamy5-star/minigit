package com.minigit.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

/**
 * Commit history (F7) and restore (F8).
 * Every object is checksum-verified before it is used.
 */
public class HistoryService {

    private final ObjectStore store;

    public HistoryService(ObjectStore store) {
        this.store = store;
    }

    /** Loads one commit by hash. */
    public Commit getCommit(String hash) {
        verifyObject(hash);
        StorageObject obj = store.read(hash);
        if (!(obj instanceof Commit commit)) {
            throw new IllegalArgumentException("Not a commit: " + hash);
        }
        return commit;
    }

    /**
     * Returns the commit and all its ancestors, newest first.
     * Handles merge commits (2 parents) and never lists a commit twice.
     */
    public List<LogEntry> log(String startHash) {
        List<LogEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        PriorityQueue<LogEntry> queue = new PriorityQueue<>(
                Comparator.comparing((LogEntry e) -> e.commit().getTime()).reversed());

        queue.add(new LogEntry(startHash, getCommit(startHash)));
        seen.add(startHash);

        while (!queue.isEmpty()) {
            LogEntry entry = queue.poll();
            result.add(entry);
            for (String parentHash : entry.commit().getParentHashes()) {
                if (seen.add(parentHash)) {
                    queue.add(new LogEntry(parentHash, getCommit(parentHash)));
                }
            }
        }
        return result;
    }

    /**
     * All files of a commit as path -> content, e.g. "src/Main.java" -> bytes.
     * Sorted by path. Throws if any object is missing or corrupted.
     */
    public Map<String, byte[]> snapshot(String commitHash) {
        Commit commit = getCommit(commitHash);
        Map<String, byte[]> files = new TreeMap<>();
        collectFiles(commit.getTreeHash(), "", files);
        return files;
    }

    /**
     * Restores a commit into targetDir (F8). The whole snapshot is read and
     * verified first, so a corrupted object means nothing is written.
     * Existing files with the same path are overwritten; other files are left alone.
     */
    public void restore(String commitHash, Path targetDir) {
        Map<String, byte[]> files = snapshot(commitHash);
        Path root = targetDir.toAbsolutePath().normalize();

        try {
            Files.createDirectories(root);
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                Path file = root.resolve(entry.getKey()).normalize();
                if (!file.startsWith(root)) {
                    throw new SecurityException("Path escapes target folder: " + entry.getKey());
                }
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Restore failed", e);
        }
    }

    private void collectFiles(String treeHash, String prefix, Map<String, byte[]> out) {
        verifyObject(treeHash);
        StorageObject obj = store.read(treeHash);
        if (!(obj instanceof Tree tree)) {
            throw new IllegalStateException("Expected a tree: " + treeHash);
        }

        for (TreeEntry entry : tree.getEntries()) {
            String path = prefix + entry.name();
            switch (entry.type()) {
                case "tree" -> collectFiles(entry.hash(), path + "/", out);
                case "blob" -> {
                    verifyObject(entry.hash());
                    StorageObject blobObj = store.read(entry.hash());
                    if (!(blobObj instanceof Blob blob)) {
                        throw new IllegalStateException("Expected a blob: " + entry.hash());
                    }
                    out.put(path, blob.serializeBody());
                }
                default -> throw new IllegalStateException("Unknown tree entry type: " + entry.type());
            }
        }
    }

    private void verifyObject(String hash) {
        if (!store.verify(hash)) {
            throw new IllegalStateException("Object is missing or corrupted: " + hash);
        }
    }
}
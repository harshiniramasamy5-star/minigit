package com.minigit.storage.diff;

import com.minigit.storage.HistoryService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Compares two commits file by file using the DiffAlgorithm (F9). */
public class DiffService {

    private final HistoryService history;
    private final DiffAlgorithm algorithm;

    public DiffService(HistoryService history, DiffAlgorithm algorithm) {
        this.history = history;
        this.algorithm = algorithm;
    }

    /**
     * Diff between two commits. Pass null as oldHash to compare against an
     * empty project (useful for the very first commit).
     * Unchanged files are not listed.
     */
    public List<FileDiff> diffCommits(String oldHash, String newHash) {
        Map<String, byte[]> oldFiles = load(oldHash);
        Map<String, byte[]> newFiles = load(newHash);

        Set<String> paths = new TreeSet<>(oldFiles.keySet());
        paths.addAll(newFiles.keySet());

        List<FileDiff> result = new ArrayList<>();
        for (String path : paths) {
            byte[] before = oldFiles.get(path);
            byte[] after = newFiles.get(path);

            if (before == null) {
                result.add(new FileDiff(path, FileStatus.ADDED,
                        algorithm.diff("", text(after))));
            } else if (after == null) {
                result.add(new FileDiff(path, FileStatus.DELETED,
                        algorithm.diff(text(before), "")));
            } else if (!Arrays.equals(before, after)) {
                result.add(new FileDiff(path, FileStatus.MODIFIED,
                        algorithm.diff(text(before), text(after))));
            }
        }
        return result;
    }

    private Map<String, byte[]> load(String commitHash) {
        return commitHash == null ? Map.of() : history.snapshot(commitHash);
    }

    private static String text(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}
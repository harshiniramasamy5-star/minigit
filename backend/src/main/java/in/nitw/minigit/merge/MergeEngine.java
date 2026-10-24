package in.nitw.minigit.merge;

import in.nitw.minigit.storage.Commit;
import in.nitw.minigit.storage.HistoryService;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure merge logic over one repo's history: no database, no Spring.
 * Decides up-to-date / fast-forward / three-way and computes the file-level result.
 */
public final class MergeEngine {

    public enum Outcome { UP_TO_DATE, FAST_FORWARD, THREE_WAY }

    /** Result of planning a merge of {@code theirs} into {@code ours}. */
    public record Plan(Outcome outcome, String base, Map<String, byte[]> clean,
                       Map<String, FileConflict> conflicts) {}

    private final HistoryService history;

    public MergeEngine(HistoryService history) {
        this.history = history;
    }

    public Plan plan(String ours, String theirs) {
        if (ours.equals(theirs) || isAncestor(theirs, ours)) {
            // theirs is already contained in ours: nothing to do
            return new Plan(Outcome.UP_TO_DATE, null, Map.of(), Map.of());
        }
        if (isAncestor(ours, theirs)) {
            return new Plan(Outcome.FAST_FORWARD, ours, Map.of(), Map.of());
        }
        String base = mergeBase(ours, theirs);
        if (base == null) {
            throw new IllegalStateException("No common ancestor; unrelated histories cannot be merged");
        }
        Map<String, byte[]> b = history.snapshot(base);
        Map<String, byte[]> o = history.snapshot(ours);
        Map<String, byte[]> t = history.snapshot(theirs);

        Map<String, byte[]> clean = new TreeMap<>();
        Map<String, FileConflict> conflicts = new TreeMap<>();
        Set<String> paths = new TreeSet<>();
        paths.addAll(b.keySet());
        paths.addAll(o.keySet());
        paths.addAll(t.keySet());

        for (String path : paths) {
            byte[] bv = b.get(path), ov = o.get(path), tv = t.get(path);
            boolean oursChanged = !Arrays.equals(bv, ov);
            boolean theirsChanged = !Arrays.equals(bv, tv);
            byte[] result;
            if (!theirsChanged) result = ov;                    // only we changed it (or nobody did)
            else if (!oursChanged) result = tv;                 // only they changed it
            else if (Arrays.equals(ov, tv)) result = ov;        // both made the same change
            else {
                conflicts.put(path, new FileConflict(path, FileConflict.classify(bv, ov, tv), bv, ov, tv));
                continue;
            }
            if (result != null) clean.put(path, result);
        }
        return new Plan(Outcome.THREE_WAY, base, clean, conflicts);
    }

    /** True if {@code ancestor} is reachable from {@code descendant} (or equal). */
    public boolean isAncestor(String ancestor, String descendant) {
        return distances(descendant).containsKey(ancestor);
    }

    /** Common ancestor closest to both heads (smallest total distance). */
    public String mergeBase(String a, String b) {
        Map<String, Integer> da = distances(a), db = distances(b);
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : da.entrySet()) {
            Integer other = db.get(e.getKey());
            if (other != null && e.getValue() + other < bestScore) {
                best = e.getKey();
                bestScore = e.getValue() + other;
            }
        }
        return best;
    }

    private Map<String, Integer> distances(String start) {
        Map<String, Integer> d = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        d.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            String h = queue.remove();
            Commit c = history.getCommit(h);
            for (String p : c.getParentHashes()) {
                if (!d.containsKey(p)) {
                    d.put(p, d.get(h) + 1);
                    queue.add(p);
                }
            }
        }
        return d;
    }
}

package in.nitw.minigit.merge;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Revert as a three-way merge: base = the commit being reverted, "ours" = the branch head,
 * "theirs" = the commit's parent. A file the commit changed goes back to the parent's version if the head
 * still has the commit's version; if later commits changed it again it is a conflict. Files the commit
 * didn't touch stay as they are on the head. Pure logic, no storage.
 */
public final class RevertEngine {

    public record Result(Map<String, byte[]> files, Map<String, FileConflict> conflicts) {}

    private RevertEngine() {}

    public static Result apply(Map<String, byte[]> commit, Map<String, byte[]> parent, Map<String, byte[]> head) {
        Map<String, byte[]> files = new TreeMap<>();
        Map<String, FileConflict> conflicts = new TreeMap<>();
        Set<String> paths = new TreeSet<>();
        paths.addAll(commit.keySet());
        paths.addAll(parent.keySet());
        paths.addAll(head.keySet());

        for (String path : paths) {
            byte[] bv = commit.get(path), tv = parent.get(path), ov = head.get(path);
            boolean headChanged = !Arrays.equals(bv, ov);        // changed since the commit
            boolean revertChanges = !Arrays.equals(bv, tv);      // the commit touched this file
            byte[] result;
            if (!revertChanges) result = ov;                     // commit didn't touch it: keep the head's
            else if (!headChanged) result = tv;                  // still as the commit left it: undo it
            else if (Arrays.equals(ov, tv)) result = ov;         // already back to the parent's version
            else {
                conflicts.put(path, new FileConflict(path, FileConflict.classify(bv, ov, tv), bv, ov, tv));
                continue;
            }
            if (result != null) files.put(path, result);
        }
        return new Result(files, conflicts);
    }
}

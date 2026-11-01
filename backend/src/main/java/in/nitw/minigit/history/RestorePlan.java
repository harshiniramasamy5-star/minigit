package in.nitw.minigit.history;

import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.common.PathValidator;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/** Works out the files of the commit a restore will create (U4). */
public final class RestorePlan {

    private RestorePlan() {}

    /**
     * @param head   files at the branch head now
     * @param target files of the commit being restored
     * @param path   null/blank = the whole commit, otherwise just that file
     * @return the new file set; throws 404 if the file is in neither, 400 if nothing would change
     */
    public static Map<String, byte[]> apply(Map<String, byte[]> head, Map<String, byte[]> target, String path) {
        Map<String, byte[]> result;
        if (path == null || path.isBlank()) {
            result = new TreeMap<>(target);
        } else {
            String clean = PathValidator.normalise(path);
            result = new TreeMap<>(head);
            if (target.containsKey(clean)) {
                result.put(clean, target.get(clean));
            } else if (head.containsKey(clean)) {
                result.remove(clean);               // the file didn't exist then, so restoring it means removing it
            } else {
                throw ApiException.notFound("File " + clean);
            }
        }
        if (same(head, result)) {
            throw ApiException.badRequest("NOTHING_TO_RESTORE", "The branch already matches that version");
        }
        return result;
    }

    private static boolean same(Map<String, byte[]> a, Map<String, byte[]> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (Map.Entry<String, byte[]> e : a.entrySet()) {
            if (!Arrays.equals(e.getValue(), b.get(e.getKey()))) return false;
        }
        return true;
    }
}

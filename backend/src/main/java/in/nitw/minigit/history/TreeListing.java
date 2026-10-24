package in.nitw.minigit.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Lists one folder of a snapshot (path -> bytes), the way a file browser shows it. */
public final class TreeListing {

    public record Entry(String name, String type, long size) {}

    private TreeListing() {}

    /**
     * @param dir "" for the root, otherwise a normalised folder path like "src/main"
     * @return the entries (folders first, then files, each by name), or null if the folder doesn't exist
     */
    public static List<Entry> list(Map<String, byte[]> snapshot, String dir) {
        String prefix = dir.isEmpty() ? "" : dir + "/";
        Map<String, Entry> found = new TreeMap<>();
        boolean any = dir.isEmpty();
        for (Map.Entry<String, byte[]> f : snapshot.entrySet()) {
            String path = f.getKey();
            if (!path.startsWith(prefix)) continue;
            any = true;
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash < 0) {
                found.put(rest, new Entry(rest, "FILE", f.getValue().length));
            } else {
                String folder = rest.substring(0, slash);
                found.putIfAbsent(folder, new Entry(folder, "DIR", 0));
            }
        }
        if (!any) return null;
        List<Entry> out = new ArrayList<>(found.values());
        out.sort(Comparator.comparing((Entry e) -> !e.type().equals("DIR")).thenComparing(Entry::name));
        return out;
    }
}

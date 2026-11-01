package in.nitw.minigit.merge;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class MergeSessionTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private MergeSession session() {
        Map<String, FileConflict> c = new TreeMap<>();
        c.put("a.txt", new FileConflict("a.txt", FileConflict.Kind.BOTH_MODIFIED, b("base"), b("ours"), b("theirs")));
        c.put("d.txt", new FileConflict("d.txt", FileConflict.Kind.MODIFIED_DELETED, b("base"), b("ours"), null));
        return new MergeSession("s1", 7, 1, "feature", "main", "o", "t", "b", Map.of("keep.txt", b("k")), c);
    }

    @Test void finishBlockedUntilEverythingResolved() {
        MergeSession s = session();
        assertThrows(IllegalStateException.class, s::finalFiles);
        s.resolve("a.txt", "ours", null);
        assertEquals(1, s.unresolved().size());
        assertThrows(IllegalStateException.class, s::finalFiles);
    }

    @Test void choicesProduceExpectedFiles() {
        MergeSession s = session();
        s.resolve("a.txt", "custom", "hand merged");
        s.resolve("d.txt", "theirs", null); // theirs deleted it
        Map<String, byte[]> f = s.finalFiles();
        assertEquals("hand merged", new String(f.get("a.txt"), StandardCharsets.UTF_8));
        assertFalse(f.containsKey("d.txt"));
        assertTrue(f.containsKey("keep.txt"));
    }

    @Test void badInputRejected() {
        MergeSession s = session();
        assertThrows(IllegalArgumentException.class, () -> s.resolve("nope.txt", "ours", null));
        assertThrows(IllegalArgumentException.class, () -> s.resolve("a.txt", "mine", null));
        assertThrows(IllegalArgumentException.class, () -> s.resolve("a.txt", "custom", null));
        assertThrows(IllegalArgumentException.class, () -> s.resolve("a.txt", null, null));
    }

    @Test void textConflictExposesText() {
        FileConflict c = session().conflicts().get("a.txt");
        assertFalse(c.binary());
        assertEquals("theirs", c.theirsText());
    }

    @Test void storedResolutionsCanBeReplayed() {
        MergeSession s = session();
        s.resolveRaw("a.txt", b("stored"));   // e.g. a MANUAL row loaded from the database
        s.resolveRaw("d.txt", null);          // a stored "deleted"
        s.resolveRaw("unknown.txt", b("x"));  // unknown paths are ignored
        Map<String, byte[]> f = s.finalFiles();
        assertEquals("stored", new String(f.get("a.txt"), StandardCharsets.UTF_8));
        assertFalse(f.containsKey("d.txt"));
        assertFalse(f.containsKey("unknown.txt"));
        assertArrayEquals(b("stored"), s.resolvedValue("a.txt"));
    }
}

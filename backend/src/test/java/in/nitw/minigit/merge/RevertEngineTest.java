package in.nitw.minigit.merge;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevertEngineTest {

    private static Map<String, byte[]> files(String... kv) {
        Map<String, byte[]> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1].getBytes(StandardCharsets.UTF_8));
        return m;
    }

    private static String text(RevertEngine.Result r, String path) {
        byte[] b = r.files().get(path);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void revertingAnAddedFileRemovesIt() {
        var r = RevertEngine.apply(files("a", "1", "b", "new"), files("a", "1"), files("a", "1", "b", "new"));
        assertTrue(r.conflicts().isEmpty());
        assertEquals(null, text(r, "b"));
        assertEquals("1", text(r, "a"));
    }

    @Test
    void revertingAModificationRestoresTheParentVersion() {
        var r = RevertEngine.apply(files("x", "2"), files("x", "1"), files("x", "2"));
        assertEquals("1", text(r, "x"));
    }

    @Test
    void revertingADeletionBringsTheFileBack() {
        var r = RevertEngine.apply(files("a", "1"), files("a", "1", "y", "old"), files("a", "1"));
        assertEquals("old", text(r, "y"));
    }

    @Test
    void laterChangeToTheSameFileIsAConflict() {
        var r = RevertEngine.apply(files("x", "2"), files("x", "1"), files("x", "3"));
        assertEquals(1, r.conflicts().size());
        assertEquals(FileConflict.Kind.BOTH_MODIFIED, r.conflicts().get("x").kind());
    }

    @Test
    void filesTheCommitDidNotTouchKeepTheirLaterChanges() {
        var r = RevertEngine.apply(files("x", "2", "z", "1"), files("x", "1", "z", "1"), files("x", "2", "z", "9"));
        assertEquals("1", text(r, "x"));
        assertEquals("9", text(r, "z"));
    }
}

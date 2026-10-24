package in.nitw.minigit.merge;

import in.nitw.minigit.storage.Commit;
import in.nitw.minigit.storage.HistoryService;
import in.nitw.minigit.storage.ObjectStore;
import in.nitw.minigit.storage.TreeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class MergeEngineTest {

    @TempDir Path temp;

    private ObjectStore store;
    private MergeEngine engine;

    private void setUp() {
        store = new ObjectStore(temp.resolve("objects"));
        engine = new MergeEngine(new HistoryService(store));
    }

    private String commit(Map<String, String> files, String... parents) {
        Map<String, byte[]> bytes = new TreeMap<>();
        files.forEach((k, v) -> bytes.put(k, v.getBytes(StandardCharsets.UTF_8)));
        return commitBytes(bytes, parents);
    }

    private String commitBytes(Map<String, byte[]> files, String... parents) {
        String tree = new TreeBuilder(store).build(files);
        return store.write(new Commit(tree, List.of(parents), "test", Instant.now(), "c"));
    }

    private static String s(byte[] b) { return new String(b, StandardCharsets.UTF_8); }

    @Test void sameHeadIsUpToDate() {
        setUp();
        String a = commit(Map.of("a.txt", "1"));
        assertEquals(MergeEngine.Outcome.UP_TO_DATE, engine.plan(a, a).outcome());
    }

    @Test void alreadyMergedSourceIsUpToDateNotAnEmptyMergeCommit() {
        setUp();
        String root = commit(Map.of("a.txt", "1"));
        String ahead = commit(Map.of("a.txt", "2"), root);
        // target is ahead of source: source is already contained in target
        assertEquals(MergeEngine.Outcome.UP_TO_DATE, engine.plan(ahead, root).outcome());
    }

    @Test void targetBehindSourceFastForwards() {
        setUp();
        String root = commit(Map.of("a.txt", "1"));
        String ahead = commit(Map.of("a.txt", "2"), root);
        MergeEngine.Plan p = engine.plan(root, ahead);
        assertEquals(MergeEngine.Outcome.FAST_FORWARD, p.outcome());
    }

    @Test void divergedWithoutOverlapMergesCleanly() {
        setUp();
        String root = commit(Map.of("a.txt", "base", "gone.txt", "x"));
        String ours = commit(Map.of("a.txt", "ours", "ours.txt", "yes", "gone.txt", "x"), root);
        String theirs = commit(Map.of("a.txt", "base", "theirs.txt", "yes"), root); // they deleted gone.txt
        MergeEngine.Plan p = engine.plan(ours, theirs);
        assertEquals(MergeEngine.Outcome.THREE_WAY, p.outcome());
        assertEquals(root, p.base());
        assertTrue(p.conflicts().isEmpty());
        assertEquals("ours", s(p.clean().get("a.txt")));
        assertTrue(p.clean().containsKey("ours.txt"));
        assertTrue(p.clean().containsKey("theirs.txt"));
        assertFalse(p.clean().containsKey("gone.txt"));
    }

    @Test void sameChangeOnBothSidesIsNotAConflict() {
        setUp();
        String root = commit(Map.of("a.txt", "base", "o.txt", "1"));
        String ours = commit(Map.of("a.txt", "same", "o.txt", "2"), root);
        String theirs = commit(Map.of("a.txt", "same", "o.txt", "1", "t.txt", "t"), root);
        MergeEngine.Plan p = engine.plan(ours, theirs);
        assertTrue(p.conflicts().isEmpty());
        assertEquals("same", s(p.clean().get("a.txt")));
    }

    @Test void conflictKindsAreClassified() {
        setUp();
        String root = commit(Map.of("mod.txt", "b", "md.txt", "b", "dm.txt", "b"));
        String ours = commit(Map.of("mod.txt", "o", "md.txt", "o", "add.txt", "o"), root);
        String theirs = commit(Map.of("mod.txt", "t", "dm.txt", "t", "add.txt", "t"), root);
        Map<String, FileConflict> c = engine.plan(ours, theirs).conflicts();
        assertEquals(FileConflict.Kind.BOTH_MODIFIED, c.get("mod.txt").kind());
        assertEquals(FileConflict.Kind.MODIFIED_DELETED, c.get("md.txt").kind());
        assertEquals(FileConflict.Kind.DELETED_MODIFIED, c.get("dm.txt").kind());
        assertEquals(FileConflict.Kind.BOTH_ADDED, c.get("add.txt").kind());
        assertEquals(4, c.size());
    }

    @Test void unrelatedHistoriesAreRejected() {
        setUp();
        String a = commit(Map.of("a.txt", "1"));
        String b = commit(Map.of("b.txt", "2"));
        assertThrows(IllegalStateException.class, () -> engine.plan(a, b));
    }

    @Test void mergeBaseAfterEarlierMergeIsTheLastMergedCommit() {
        setUp();
        String root = commit(Map.of("a.txt", "0"));
        String f1 = commit(Map.of("a.txt", "0", "f.txt", "1"), root);
        String m1 = commit(Map.of("a.txt", "1", "f.txt", "1"), root, f1);  // main merged feature once
        String f2 = commit(Map.of("a.txt", "0", "f.txt", "2"), f1);
        assertEquals(f1, engine.mergeBase(m1, f2));
    }

    @Test void binaryConflictKeepsExactBytes() {
        setUp();
        byte[] base = {0, 1, 2}, ours = {0, (byte) 0xFF, 2}, theirs = {0, (byte) 0xFE, 9};
        String root = commitBytes(Map.of("img.bin", base));
        String o = commitBytes(Map.of("img.bin", ours), root);
        String t = commitBytes(Map.of("img.bin", theirs), root);
        FileConflict c = engine.plan(o, t).conflicts().get("img.bin");
        assertTrue(c.binary());
        assertNull(c.oursText());
        MergeSession session = new MergeSession("s", 1, 1, "f", "main", o, t, root, Map.of(),
                Map.of("img.bin", c));
        assertThrows(IllegalArgumentException.class, () -> session.resolve("img.bin", "custom", "x"));
        session.resolve("img.bin", "theirs", null);
        assertArrayEquals(theirs, session.finalFiles().get("img.bin"));
    }
}

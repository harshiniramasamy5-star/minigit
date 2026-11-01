package in.nitw.minigit.history;

import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.storage.StoredCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** TreeListing, RestorePlan and TextCheck, plus the engine round trip the commit and restore services rely on. */
class HistoryLogicTest {

    @TempDir
    Path root;

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static Map<String, byte[]> files(String... kv) {
        Map<String, byte[]> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], b(kv[i + 1]));
        return m;
    }

    @Test
    void treeListingShowsFoldersFirstAndNestsCorrectly() {
        Map<String, byte[]> snap = files("README.md", "hi", "src/Main.java", "x", "src/util/Fmt.java", "yy", "docs/a.md", "z");
        assertEquals(List.of(
                new TreeListing.Entry("docs", "DIR", 0),
                new TreeListing.Entry("src", "DIR", 0),
                new TreeListing.Entry("README.md", "FILE", 2)), TreeListing.list(snap, ""));
        assertEquals(List.of(
                new TreeListing.Entry("util", "DIR", 0),
                new TreeListing.Entry("Main.java", "FILE", 1)), TreeListing.list(snap, "src"));
        assertEquals(List.of(new TreeListing.Entry("Fmt.java", "FILE", 2)), TreeListing.list(snap, "src/util"));
        assertNull(TreeListing.list(snap, "nope"));
        assertNull(TreeListing.list(snap, "sr"));        // a prefix of a name is not a folder
        assertEquals(List.of(), TreeListing.list(Map.of(), ""));
    }

    @Test
    void restoreWholeCommitReplacesEverything() {
        Map<String, byte[]> head = files("a.txt", "new", "extra.txt", "e");
        Map<String, byte[]> old = files("a.txt", "old", "gone.txt", "g");
        Map<String, byte[]> result = RestorePlan.apply(head, old, null);
        assertEquals(List.of("a.txt", "gone.txt"), List.copyOf(result.keySet()));
        assertArrayEquals(b("old"), result.get("a.txt"));
    }

    @Test
    void restoreOneFileLeavesTheRestAlone() {
        Map<String, byte[]> head = files("a.txt", "new", "b.txt", "keep");
        Map<String, byte[]> old = files("a.txt", "old", "b.txt", "other");
        Map<String, byte[]> result = RestorePlan.apply(head, old, "./a.txt");
        assertArrayEquals(b("old"), result.get("a.txt"));
        assertArrayEquals(b("keep"), result.get("b.txt"));
    }

    @Test
    void restoringAFileThatDidNotExistThenRemovesIt() {
        Map<String, byte[]> head = files("a.txt", "1", "later.txt", "2");
        Map<String, byte[]> old = files("a.txt", "1");
        assertEquals(List.of("a.txt"), List.copyOf(RestorePlan.apply(head, old, "later.txt").keySet()));
    }

    @Test
    void restoreRefusesNoOpsAndUnknownFiles() {
        Map<String, byte[]> same = files("a.txt", "1");
        ApiException noop = assertThrows(ApiException.class, () -> RestorePlan.apply(same, files("a.txt", "1"), null));
        assertEquals("NOTHING_TO_RESTORE", noop.getCode());
        ApiException missing = assertThrows(ApiException.class, () -> RestorePlan.apply(same, same, "zzz.txt"));
        assertEquals(404, missing.getStatus().value());
        assertThrows(IllegalArgumentException.class, () -> RestorePlan.apply(same, files("a.txt", "2"), "../x"));
    }

    @Test
    void textCheckSeparatesTextFromBinary() {
        assertTrue(TextCheck.isText(b("héllo\nwörld")));
        assertTrue(TextCheck.isText(new byte[0]));
        assertFalse(TextCheck.isText(new byte[]{'a', 0, 'b'}));
        assertFalse(TextCheck.isText(new byte[]{(byte) 0xFF, (byte) 0xFE, 'x'}));
    }

    @Test
    void commitThenRestoreRoundTripsThroughTheRealEngine() {
        RepoObjectStores stores = new RepoObjectStores(root);
        long repo = 5;
        String emptyTree = stores.buildTree(repo, Map.of());
        StoredCommit first = stores.writeCommit(repo, emptyTree, List.of(), "asha", "Initial commit");
        assertTrue(stores.history(repo).snapshot(first.hash()).isEmpty());

        String t2 = stores.buildTree(repo, files("./src//A.java", "v1", "README.md", "hi"));
        StoredCommit second = stores.writeCommit(repo, t2, List.of(first.hash()), "asha", "add files");
        Map<String, byte[]> snap = stores.history(repo).snapshot(second.hash());
        assertEquals(List.of("README.md", "src/A.java"), List.copyOf(snap.keySet()));

        Map<String, byte[]> back = RestorePlan.apply(snap, stores.history(repo).snapshot(first.hash()), null);
        assertTrue(back.isEmpty());
        assertEquals(emptyTree, stores.buildTree(repo, back));
    }
}

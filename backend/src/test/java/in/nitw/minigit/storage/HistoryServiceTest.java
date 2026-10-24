package in.nitw.minigit.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoryServiceTest {

    @TempDir
    Path tmp;

    private ObjectStore store;
    private HistoryService history;

    @BeforeEach
    void setUp() {
        store = new ObjectStore(tmp.resolve("objects"));
        history = new HistoryService(store);
    }

    private String blob(String content) {
        return store.write(new Blob(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String commit(String treeHash, String parent, long millis) {
        List<String> parents = parent == null ? List.of() : List.of(parent);
        return store.write(new Commit(treeHash, parents, "alice", Instant.ofEpochMilli(millis), "msg"));
    }

    private String simpleCommit(String parent, String fileName, String content, long millis) {
        String tree = store.write(new Tree(List.of(new TreeEntry("blob", fileName, blob(content)))));
        return commit(tree, parent, millis);
    }

    @Test
    void logIsNewestFirst() {
        String c1 = simpleCommit(null, "a.txt", "one", 1000);
        String c2 = simpleCommit(c1, "a.txt", "two", 2000);
        String c3 = simpleCommit(c2, "a.txt", "three", 3000);

        List<LogEntry> log = history.log(c3);

        assertEquals(3, log.size());
        assertEquals(c3, log.get(0).hash());
        assertEquals(c2, log.get(1).hash());
        assertEquals(c1, log.get(2).hash());
    }

    @Test
    void logFromMiddleCommitOnlyShowsAncestors() {
        String c1 = simpleCommit(null, "a.txt", "one", 1000);
        String c2 = simpleCommit(c1, "a.txt", "two", 2000);
        simpleCommit(c2, "a.txt", "three", 3000);

        assertEquals(2, history.log(c2).size());
    }

    @Test
    void snapshotReadsFileContent() {
        String c1 = simpleCommit(null, "a.txt", "hello", 1000);

        Map<String, byte[]> files = history.snapshot(c1);

        assertEquals(1, files.size());
        assertEquals("hello", new String(files.get("a.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void snapshotReadsNestedFolders() {
        String fileBlob = blob("nested content");
        String srcTree = store.write(new Tree(List.of(new TreeEntry("blob", "Main.java", fileBlob))));
        String rootTree = store.write(new Tree(List.of(
                new TreeEntry("tree", "src", srcTree),
                new TreeEntry("blob", "readme.txt", blob("readme")))));
        String c1 = commit(rootTree, null, 1000);

        Map<String, byte[]> files = history.snapshot(c1);

        assertEquals(2, files.size());
        assertTrue(files.containsKey("src/Main.java"));
        assertTrue(files.containsKey("readme.txt"));
    }

    @Test
    void restoreWritesFilesToDisk() throws IOException {
        String c1 = simpleCommit(null, "a.txt", "restored text", 1000);
        Path target = tmp.resolve("out");

        history.restore(c1, target);

        assertEquals("restored text", Files.readString(target.resolve("a.txt")));
    }

    @Test
    void restoreOldCommitGivesOldContent() throws IOException {
        String c1 = simpleCommit(null, "a.txt", "version 1", 1000);
        simpleCommit(c1, "a.txt", "version 2", 2000);
        Path target = tmp.resolve("out");

        history.restore(c1, target);

        assertEquals("version 1", Files.readString(target.resolve("a.txt")));
    }

    @Test
    void corruptedBlobIsDetectedAndNothingIsWritten() throws IOException {
        String blobHash = blob("good content");
        String tree = store.write(new Tree(List.of(new TreeEntry("blob", "a.txt", blobHash))));
        String c1 = commit(tree, null, 1000);

        // tamper with the stored blob file
        Path blobFile = tmp.resolve("objects")
                .resolve(blobHash.substring(0, 2))
                .resolve(blobHash.substring(2));
        Files.writeString(blobFile, "tampered");

        Path target = tmp.resolve("out");
        assertThrows(IllegalStateException.class, () -> history.restore(c1, target));
        assertFalse(Files.exists(target.resolve("a.txt")));
    }

    @Test
    void getCommitRejectsNonCommitObjects() {
        String blobHash = blob("x");
        assertThrows(IllegalArgumentException.class, () -> history.getCommit(blobHash));
    }
}
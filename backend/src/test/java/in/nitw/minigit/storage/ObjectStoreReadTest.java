package in.nitw.minigit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObjectStoreReadTest {

    @TempDir
    Path tempDir;

    @Test
    void readsBlobBack() {
        ObjectStore store = new ObjectStore(tempDir);
        String hash = store.write(new Blob("hello".getBytes()));

        Blob back = (Blob) store.read(hash);

        assertArrayEquals("hello".getBytes(), back.getContent());
    }

    @Test
    void readsTreeBack() {
        ObjectStore store = new ObjectStore(tempDir);
        Tree tree = new Tree(List.of(
                new TreeEntry("blob", "a.txt", "1".repeat(64)),
                new TreeEntry("tree", "src", "2".repeat(64))));
        String hash = store.write(tree);

        Tree back = (Tree) store.read(hash);

        assertEquals(tree.getEntries(), back.getEntries());
    }

    @Test
    void wholeFlowBuildCommitReadBack() {
        ObjectStore store = new ObjectStore(tempDir);
        TreeBuilder builder = new TreeBuilder(store);

        String root = builder.build(Map.of(
                "README.md", "hi".getBytes(),
                "src/App.java", "class App {}".getBytes()));
        String commitHash = store.write(
                new Commit(root, List.of(), "alice", Instant.now(), "init"));

        Commit commit = (Commit) store.read(commitHash);
        Tree rootTree = (Tree) store.read(commit.getTreeHash());

        assertEquals(2, rootTree.getEntries().size());
        assertTrue(rootTree.getEntries().stream()
                .anyMatch(e -> e.name().equals("src") && e.type().equals("tree")));
    }
}
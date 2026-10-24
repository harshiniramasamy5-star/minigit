package in.nitw.minigit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import in.nitw.minigit.common.PathValidator.InvalidPathException;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the per-repository folder layout and that repositories don't see each other's objects. */
class RepoObjectStoresTest {

    @TempDir
    Path root;

    @Test
    void objectsLiveUnderRepoIdFolder() {
        RepoObjectStores stores = new RepoObjectStores(root);
        String hash = stores.objects(7).write(new Blob("x".getBytes()));

        assertTrue(Files.exists(root.resolve("7/objects").resolve(hash.substring(0, 2)).resolve(hash.substring(2))));
        assertEquals(root.resolve("7/worktrees/3"), stores.worktree(7, 3));
    }

    @Test
    void repositoriesAreIsolated() {
        RepoObjectStores stores = new RepoObjectStores(root);
        String hash = stores.objects(1).write(new Blob("secret".getBytes()));

        assertTrue(stores.objects(1).exists(hash));
        assertFalse(stores.objects(2).exists(hash));
    }

    @Test
    void fullFlowThroughFactory() {
        RepoObjectStores stores = new RepoObjectStores(root);
        long repo = 5;

        String t1 = stores.treeBuilder(repo).build(Map.of("a.txt", "one\n".getBytes(StandardCharsets.UTF_8)));
        String c1 = stores.objects(repo).write(new Commit(t1, List.of(), "harshini", Instant.ofEpochMilli(1000), "first"));
        String t2 = stores.treeBuilder(repo).build(Map.of("a.txt", "two\n".getBytes(StandardCharsets.UTF_8)));
        String c2 = stores.objects(repo).write(new Commit(t2, List.of(c1), "harshini", Instant.ofEpochMilli(2000), "second"));

        assertEquals(List.of(c2, c1), stores.history(repo).log(c2).stream().map(LogEntry::hash).toList());
        assertEquals(1, stores.diff(repo).diffCommits(c1, c2).size());
    }

    @Test
    void badIdsAreRejected() {
        RepoObjectStores stores = new RepoObjectStores(root);
        assertThrows(IllegalArgumentException.class, () -> stores.objects(0));
        assertThrows(IllegalArgumentException.class, () -> stores.worktree(1, -1));
    }

    @Test
    void buildTreeNormalisesClientPaths() {
        RepoObjectStores stores = new RepoObjectStores(root);
        String strict = stores.treeBuilder(1).build(Map.of("a/b.txt", "x".getBytes(), "c.txt", "y".getBytes()));
        String loose = stores.buildTree(1, Map.of("a//b.txt", "x".getBytes(), "./c.txt", "y".getBytes()));
        assertEquals(strict, loose);
    }

    @Test
    void buildTreeRejectsBadPathsAsInvalidPath() {
        RepoObjectStores stores = new RepoObjectStores(root);
        assertThrows(InvalidPathException.class, () -> stores.buildTree(1, Map.of("../evil", "x".getBytes())));
        assertThrows(InvalidPathException.class, () -> stores.buildTree(1, Map.of("/etc/passwd", "x".getBytes())));
        assertThrows(InvalidPathException.class, () -> stores.buildTree(1, Map.of("a\\b", "x".getBytes())));
        assertThrows(InvalidPathException.class, () -> stores.buildTree(1, Map.of(".minigit/HEAD", "x".getBytes())));

        Map<String, byte[]> dup = new LinkedHashMap<>();
        dup.put("a/b.txt", "1".getBytes());
        dup.put("a//b.txt", "2".getBytes());
        assertThrows(InvalidPathException.class, () -> stores.buildTree(1, dup));

        Map<String, byte[]> clash = new LinkedHashMap<>();
        clash.put("a", "file".getBytes());
        clash.put("a/b.txt", "inside".getBytes());
        assertThrows(InvalidPathException.class, () -> stores.buildTree(1, clash));
    }

    @Test
    void writeCommitReturnsTheTimeStoredInTheObject() {
        RepoObjectStores stores = new RepoObjectStores(root);
        String tree = stores.buildTree(4, Map.of("a.txt", "x".getBytes()));
        Instant now = Instant.parse("2026-10-24T10:15:30.123456789Z");

        StoredCommit c = stores.writeCommit(4, tree, List.of(), "harshini", "init", now);

        assertEquals(Instant.parse("2026-10-24T10:15:30.123Z"), c.createdAt());   // ms, like DATETIME(3)
        assertEquals(c.createdAt(), stores.history(4).getCommit(c.hash()).getTime());
    }
}

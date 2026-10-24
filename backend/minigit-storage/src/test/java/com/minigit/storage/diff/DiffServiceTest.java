package com.minigit.storage.diff;

import com.minigit.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffServiceTest {

    @TempDir
    Path tmp;

    private ObjectStore store;
    private DiffService diffService;

    @BeforeEach
    void setUp() {
        store = new ObjectStore(tmp.resolve("objects"));
        diffService = new DiffService(new HistoryService(store), new MyersDiff());
    }

    private String commit(String parent, String fileName, String content, long millis) {
        String blob = store.write(new Blob(content.getBytes(StandardCharsets.UTF_8)));
        String tree = store.write(new Tree(List.of(new TreeEntry("blob", fileName, blob))));
        List<String> parents = parent == null ? List.of() : List.of(parent);
        return store.write(new Commit(tree, parents, "alice", Instant.ofEpochMilli(millis), "msg"));
    }

    @Test
    void modifiedFileShowsInsertAndDelete() {
        String c1 = commit(null, "a.txt", "one\ntwo", 1000);
        String c2 = commit(c1, "a.txt", "one\n2", 2000);

        List<FileDiff> result = diffService.diffCommits(c1, c2);

        assertEquals(1, result.size());
        assertEquals(FileStatus.MODIFIED, result.get(0).status());
        assertTrue(result.get(0).lines().stream().anyMatch(l -> l.type() == DiffType.DELETE));
        assertTrue(result.get(0).lines().stream().anyMatch(l -> l.type() == DiffType.INSERT));
    }

    @Test
    void identicalCommitsGiveNoDiff() {
        String c1 = commit(null, "a.txt", "same", 1000);
        String c2 = commit(c1, "a.txt", "same", 2000);

        assertTrue(diffService.diffCommits(c1, c2).isEmpty());
    }

    @Test
    void renamedFileShowsAsDeletedPlusAdded() {
        String c1 = commit(null, "old.txt", "text", 1000);
        String c2 = commit(c1, "new.txt", "text", 2000);

        List<FileDiff> result = diffService.diffCommits(c1, c2);

        assertEquals(2, result.size());
        assertEquals(FileStatus.ADDED, result.get(0).status());    // new.txt sorts first
        assertEquals(FileStatus.DELETED, result.get(1).status());  // old.txt
    }

    @Test
    void nullOldCommitMeansEverythingIsAdded() {
        String c1 = commit(null, "a.txt", "hello", 1000);

        List<FileDiff> result = diffService.diffCommits(null, c1);

        assertEquals(1, result.size());
        assertEquals(FileStatus.ADDED, result.get(0).status());
    }
}

package com.minigit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommitTest {

    @TempDir
    Path tempDir;

    private static final String TREE = "a".repeat(64);
    private static final String P1 = "b".repeat(64);
    private static final String P2 = "c".repeat(64);
    private static final Instant T = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void roundTripThroughStore() {
        ObjectStore store = new ObjectStore(tempDir);
        Commit c = new Commit(TREE, List.of(P1), "alice", T, "first change");

        String hash = store.write(c);
        Commit back = (Commit) store.read(hash);

        assertEquals(TREE, back.getTreeHash());
        assertEquals(List.of(P1), back.getParentHashes());
        assertEquals("alice", back.getAuthor());
        assertEquals(T, back.getTime());
        assertEquals("first change", back.getMessage());
        assertEquals(hash, back.getHash());
    }

    @Test
    void firstCommitHasNoParent() {
        ObjectStore store = new ObjectStore(tempDir);
        Commit c = new Commit(TREE, List.of(), "alice", T, "init");

        Commit back = (Commit) store.read(store.write(c));

        assertTrue(back.getParentHashes().isEmpty());
    }

    @Test
    void mergeCommitKeepsTwoParents() {
        ObjectStore store = new ObjectStore(tempDir);
        Commit c = new Commit(TREE, List.of(P1, P2), "bob", T, "merge");

        Commit back = (Commit) store.read(store.write(c));

        assertEquals(List.of(P1, P2), back.getParentHashes());
    }

    @Test
    void multiLineMessageSurvives() {
        ObjectStore store = new ObjectStore(tempDir);
        String msg = "Title\n\nLonger explanation\nwith lines";
        Commit c = new Commit(TREE, List.of(P1), "alice", T, msg);

        Commit back = (Commit) store.read(store.write(c));

        assertEquals(msg, back.getMessage());
    }

    @Test
    void differentTimeGivesDifferentHash() {
        Commit a = new Commit(TREE, List.of(P1), "alice", T, "x");
        Commit b = new Commit(TREE, List.of(P1), "alice", T.plusSeconds(1), "x");

        assertNotEquals(a.getHash(), b.getHash());
    }

    @Test
    void badHashesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Commit("nothex", List.of(), "alice", T, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new Commit(TREE, List.of("../../etc"), "alice", T, "x"));
    }
}

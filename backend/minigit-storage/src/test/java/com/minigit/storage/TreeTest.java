package com.minigit.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeTest {

    @Test
    void entryOrderDoesNotChangeHash() {
        TreeEntry a = new TreeEntry("blob", "a.txt", "1".repeat(64));
        TreeEntry b = new TreeEntry("blob", "b.txt", "2".repeat(64));

        assertEquals(new Tree(List.of(a, b)).getHash(),
                new Tree(List.of(b, a)).getHash());
    }

    @Test
    void serializeThenParseGivesSameTree() {
        Tree original = new Tree(List.of(
                new TreeEntry("blob", "a.txt", "1".repeat(64)),
                new TreeEntry("tree", "src", "2".repeat(64))));

        Tree parsed = Tree.fromBody(original.serializeBodyForTest());

        assertEquals(original.getHash(), parsed.getHash());
    }

    @Test
    void badNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Tree(List.of(new TreeEntry("blob", "../x", "1".repeat(64)))));
        assertThrows(IllegalArgumentException.class,
                () -> new Tree(List.of(new TreeEntry("blob", "a/b", "1".repeat(64)))));
    }

    /** Exposes the body for tests and for the commit code later. */
}
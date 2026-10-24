package com.minigit.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectStoreTest {
    @TempDir
    Path tempDir;

    private long countFiles() throws IOException {
        try (Stream<Path> s = Files.walk(tempDir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void writeThenReadBack() {
        ObjectStore store = new ObjectStore(tempDir);
        Blob blob = new Blob("hello".getBytes());

        String hash = store.write(blob);

        assertTrue(store.exists(hash));
        assertArrayEquals(blob.serialize(), store.readRaw(hash));
    }

    @Test
    void writingSameContentTwiceStoresOneFile() throws IOException {
        ObjectStore store = new ObjectStore(tempDir);

        String h1 = store.write(new Blob("same content".getBytes()));
        String h2 = store.write(new Blob("same content".getBytes()));

        assertEquals(h1, h2);
        assertEquals(1, countFiles());
    }

    @Test
    void differentContentStoresTwoFiles() throws IOException {
        ObjectStore store = new ObjectStore(tempDir);

        store.write(new Blob("one".getBytes()));
        store.write(new Blob("two".getBytes()));

        assertEquals(2, countFiles());
    }

    @Test
    void verifyDetectsTampering() throws IOException {
        ObjectStore store = new ObjectStore(tempDir);
        String hash = store.write(new Blob("important".getBytes()));

        assertTrue(store.verify(hash));

        Path file = tempDir.resolve(hash.substring(0, 2)).resolve(hash.substring(2));
        Files.write(file, "tampered".getBytes());

        assertFalse(store.verify(hash));
    }

    @Test
    void badHashIsRejected() {
        ObjectStore store = new ObjectStore(tempDir);
        assertThrows(IllegalArgumentException.class, () -> store.exists("../../etc/passwd"));
    }
}

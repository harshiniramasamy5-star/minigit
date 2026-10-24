package com.minigit.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TreeBuilderTest {

    @TempDir
    Path tempDir;

    private long countFiles() throws IOException {
        try (Stream<Path> s = Files.walk(tempDir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void sameFilesInDifferentOrderGiveSameRootHash() {
        TreeBuilder builder = new TreeBuilder(new ObjectStore(tempDir));

        Map<String, byte[]> one = new LinkedHashMap<>();
        one.put("README.md", "readme".getBytes());
        one.put("src/App.java", "class App {}".getBytes());

        Map<String, byte[]> two = new LinkedHashMap<>();
        two.put("src/App.java", "class App {}".getBytes());
        two.put("README.md", "readme".getBytes());

        assertEquals(builder.build(one), builder.build(two));
    }

    @Test
    void changingOneByteChangesRootHash() {
        TreeBuilder builder = new TreeBuilder(new ObjectStore(tempDir));

        Map<String, byte[]> a = Map.of("src/App.java", "class App {}".getBytes());
        Map<String, byte[]> b = Map.of("src/App.java", "class Apq {}".getBytes());

        assertNotEquals(builder.build(a), builder.build(b));
    }

    @Test
    void nestedFolderCreatesBlobsAndTrees() throws IOException {
        TreeBuilder builder = new TreeBuilder(new ObjectStore(tempDir));

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("README.md", "readme".getBytes());
        files.put("src/App.java", "class App {}".getBytes());

        builder.build(files);

        // 2 blobs + tree for "src" + root tree = 4 objects
        assertEquals(4, countFiles());
    }

    @Test
    void identicalFilesAreStoredOnce() throws IOException {
        TreeBuilder builder = new TreeBuilder(new ObjectStore(tempDir));

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", "same".getBytes());
        files.put("b.txt", "same".getBytes());

        builder.build(files);

        // 1 blob (dedup) + root tree = 2 objects
        assertEquals(2, countFiles());
    }

    @Test
    void dangerousPathsAreRejected() {
        TreeBuilder builder = new TreeBuilder(new ObjectStore(tempDir));

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(Map.of("../evil.txt", "x".getBytes())));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(Map.of("/etc/passwd", "x".getBytes())));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(Map.of("a//b.txt", "x".getBytes())));
    }

    @Test
    void fileAndFolderWithSameNameIsRejected() {
        TreeBuilder builder = new TreeBuilder(new ObjectStore(tempDir));

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a", "file".getBytes());
        files.put("a/b.txt", "inside".getBytes());

        assertThrows(IllegalArgumentException.class, () -> builder.build(files));
    }
}
package in.nitw.minigit.commit;

import in.nitw.minigit.common.PathValidator.InvalidPathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class WorkingTreeTest {

    @TempDir
    Path dir;

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void writeReadAndListFiles() {
        assertEquals("src/Main.java", WorkingTree.write(dir, "./src//Main.java", b("class Main {}")));
        WorkingTree.write(dir, "README.md", b("hi"));
        Map<String, byte[]> files = WorkingTree.read(dir);
        assertEquals(List.of("README.md", "src/Main.java"), List.copyOf(files.keySet()));
        assertArrayEquals(b("hi"), WorkingTree.readFile(dir, "README.md"));
        assertNull(WorkingTree.readFile(dir, "nope.txt"));
    }

    @Test
    void changesAreComparedAgainstTheBaseCommit() {
        Map<String, byte[]> base = new TreeMap<>(Map.of("a.txt", b("1"), "b.txt", b("2"), "c.txt", b("3")));
        Map<String, byte[]> work = new TreeMap<>(Map.of("a.txt", b("1"), "b.txt", b("two"), "d.txt", b("4")));
        assertEquals(List.of(
                new WorkingTree.Change("b.txt", WorkingTree.Status.MODIFIED),
                new WorkingTree.Change("c.txt", WorkingTree.Status.DELETED),
                new WorkingTree.Change("d.txt", WorkingTree.Status.ADDED)),
                WorkingTree.changes(base, work));
        assertTrue(WorkingTree.changes(base, base).isEmpty());
    }

    @Test
    void pathsCannotEscapeTheWorkingCopy() {
        for (String bad : new String[]{"../x", "/etc/passwd", "a/../../x", "..", "C:\\x", "a\\b", ".minigit/config", ""}) {
            assertThrows(InvalidPathException.class, () -> WorkingTree.write(dir, bad, b("x")), bad);
        }
        try (var s = Files.list(dir)) {
            assertEquals(0, s.count());
        } catch (java.io.IOException e) {
            fail(e);
        }
    }

    @Test
    void fileAndFolderWithTheSameNameClash() {
        WorkingTree.write(dir, "a", b("file"));
        assertThrows(InvalidPathException.class, () -> WorkingTree.write(dir, "a/b.txt", b("x")));
        WorkingTree.write(dir, "d/e.txt", b("x"));
        assertThrows(InvalidPathException.class, () -> WorkingTree.write(dir, "d", b("x")));
    }

    @Test
    void deleteRemovesTheFileAndEmptyFolders() {
        WorkingTree.write(dir, "x/y/z.txt", b("1"));
        WorkingTree.write(dir, "x/keep.txt", b("2"));
        assertTrue(WorkingTree.delete(dir, "x/y/z.txt"));
        assertFalse(Files.exists(dir.resolve("x/y")));
        assertTrue(Files.exists(dir.resolve("x/keep.txt")));
        assertFalse(WorkingTree.delete(dir, "x/y/z.txt"));
        assertTrue(WorkingTree.delete(dir, "x/keep.txt"));
        assertFalse(Files.exists(dir.resolve("x")));
        assertTrue(Files.exists(dir));
    }
}

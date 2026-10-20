package in.nitw.minigit.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathValidatorTest {

    @Test
    void normalisesSafePaths() {
        assertEquals("src/Main.java", PathValidator.normalise("src/Main.java"));
        assertEquals("src/Main.java", PathValidator.normalise("./src//Main.java"));
        assertEquals("a/b", PathValidator.normalise("a/b/"));
        assertEquals("notes..txt", PathValidator.normalise("notes..txt"));   // ".." inside a name is fine
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd", "a/../../b", "a/..", "/etc/passwd", "C:/Windows", "c:file",
            "a\\b", "..\\x", "", "   ", "./", ".minigit/HEAD", ".MiniGit/x", "a\u0000b", "a\nb"
    })
    void rejectsUnsafePaths(String raw) {
        assertThrows(PathValidator.InvalidPathException.class, () -> PathValidator.normalise(raw));
    }

    @Test
    void rejectsNullAndTooLong() {
        assertThrows(PathValidator.InvalidPathException.class, () -> PathValidator.normalise(null));
        assertThrows(PathValidator.InvalidPathException.class,
                () -> PathValidator.normalise("a".repeat(PathValidator.MAX_PATH_LENGTH + 1)));
    }

    @Test
    void resolveInsideStaysUnderRoot() {
        Path root = Path.of("/data/repos/42");
        Path p = PathValidator.resolveInside(root, "src/Main.java");
        assertTrue(p.startsWith(root));
        assertEquals(root.resolve("src/Main.java"), p);
    }
}

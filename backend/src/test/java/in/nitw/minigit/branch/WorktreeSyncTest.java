package in.nitw.minigit.branch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorktreeSyncTest {

    @TempDir Path temp;

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test void switchingRemovesFilesTheTargetDoesNotHave() throws Exception {
        Path wt = temp.resolve("wt");
        WorktreeSync.sync(Map.of("a.txt", b("1"), "only-feature/x.txt", b("x")), wt);
        assertTrue(Files.exists(wt.resolve("only-feature/x.txt")));

        WorktreeSync.sync(Map.of("a.txt", b("2"), "src/Main.java", b("m")), wt);
        assertFalse(Files.exists(wt.resolve("only-feature/x.txt")));
        assertFalse(Files.exists(wt.resolve("only-feature")));
        assertEquals("2", Files.readString(wt.resolve("a.txt")));
        assertTrue(Files.exists(wt.resolve("src/Main.java")));
    }

    @Test void pathEscapeRejected() {
        assertThrows(SecurityException.class, () -> WorktreeSync.sync(Map.of("../evil.txt", b("x")), temp.resolve("wt")));
    }
}

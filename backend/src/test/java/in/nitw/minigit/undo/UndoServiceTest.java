package in.nitw.minigit.undo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UndoServiceTest {

    @Test void descriptionShowsActionAndShortHash() {
        assertEquals("commit 3fa9c1d", UndoService.describe("COMMIT", "3fa9c1d" + "0".repeat(57)));
        assertEquals("merge abc", UndoService.describe("MERGE", "abc"));
    }
}

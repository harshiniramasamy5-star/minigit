package in.nitw.minigit.undo;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UndoStackTest {

    private static HeadMoveCommand.HeadMover cas(AtomicReference<String> head) {
        return (expected, next) -> head.compareAndSet(expected, next);
    }

    @Test void undoMovesHeadBackInOrder() {
        AtomicReference<String> head = new AtomicReference<>("c2");
        UndoStack s = new UndoStack(10);
        s.push(new HeadMoveCommand(HeadMoveCommand.Kind.COMMIT, "c0", "c1", cas(head)));
        s.push(new HeadMoveCommand(HeadMoveCommand.Kind.MERGE, "c1", "c2", cas(head)));
        s.undo();
        assertEquals("c1", head.get());
        s.undo();
        assertEquals("c0", head.get());
        assertFalse(s.canUndo());
        assertThrows(IllegalStateException.class, s::undo);
    }

    @Test void undoRefusedIfSomeoneMovedTheHeadAndEntryIsKept() {
        AtomicReference<String> head = new AtomicReference<>("c1");
        UndoStack s = new UndoStack(10);
        s.push(new HeadMoveCommand(HeadMoveCommand.Kind.COMMIT, "c0", "c1", cas(head)));
        head.set("someone-elses-commit");
        assertThrows(IllegalStateException.class, s::undo);
        assertTrue(s.canUndo());
        assertEquals("someone-elses-commit", head.get());
    }

    @Test void stackIsCapped() {
        AtomicReference<String> head = new AtomicReference<>("x");
        UndoStack s = new UndoStack(2);
        for (int i = 0; i < 5; i++) s.push(new HeadMoveCommand(HeadMoveCommand.Kind.COMMIT, "a" + i, "b" + i, cas(head)));
        assertEquals(2, s.entries().size());
    }
}

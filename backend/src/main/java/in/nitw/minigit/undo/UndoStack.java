package in.nitw.minigit.undo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Per-branch undo stack (SRS: undo covers commit, restore, merge; redo is optional, not built). */
public final class UndoStack {

    private final Deque<Command> done = new ArrayDeque<>();
    private final int limit;

    public UndoStack(int limit) {
        this.limit = limit;
    }

    /** Records an action that has ALREADY happened (the service did the head move itself). */
    public synchronized void push(Command c) {
        done.push(c);
        while (done.size() > limit) done.removeLast();
    }

    public synchronized boolean canUndo() {
        return !done.isEmpty();
    }

    /** Undoes the latest action. If undo fails (head moved), the entry stays on the stack. */
    public synchronized Command undo() {
        if (done.isEmpty()) throw new IllegalStateException("Nothing to undo");
        Command c = done.peek();
        c.undo();
        done.pop();
        return c;
    }

    public synchronized List<String> entries() {
        return done.stream().map(Command::description).toList();
    }

    public synchronized void clear() {
        done.clear();
    }
}

package in.nitw.minigit.undo;

/**
 * Moving a branch head from {@code before} to {@code after}. Commit, restore and merge all
 * end with exactly this, so one command class covers every undoable action in the SRS.
 * Commit objects are immutable; undo only moves the pointer back.
 */
public final class HeadMoveCommand implements Command {

    /** Compare-and-set on the branch head. Returns false if the head isn't {@code expected}. */
    @FunctionalInterface
    public interface HeadMover {
        boolean move(String expected, String next);
    }

    public enum Kind { COMMIT, RESTORE, MERGE }

    private final Kind kind;
    private final String before;
    private final String after;
    private final HeadMover mover;

    public HeadMoveCommand(Kind kind, String before, String after, HeadMover mover) {
        this.kind = kind;
        this.before = before;
        this.after = after;
        this.mover = mover;
    }

    @Override
    public void execute() {
        if (!mover.move(before, after)) {
            throw new IllegalStateException("Branch head moved; cannot redo " + description());
        }
    }

    @Override
    public void undo() {
        if (!mover.move(after, before)) {
            throw new IllegalStateException("Branch head moved; cannot undo " + description());
        }
    }

    @Override
    public String description() {
        return kind.name().toLowerCase() + " " + after.substring(0, Math.min(7, after.length()));
    }

    public Kind kind() { return kind; }
    public String before() { return before; }
    public String after() { return after; }
}

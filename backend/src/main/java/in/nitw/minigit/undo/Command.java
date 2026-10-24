package in.nitw.minigit.undo;

/** Command pattern: an action that can be done and undone. */
public interface Command {
    void execute();
    void undo();
    String description();
}

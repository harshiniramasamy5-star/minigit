package in.nitw.minigit.undo;

import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One undo stack per (repo, branch). U3 commit and U4 restore should call {@link #record}
 * right after they move a head, the same way MergeService does.
 *
 * Stacks are in memory for now: a restart clears undo history (commits themselves are safe).
 */
@Service
public class UndoService {

    private static final int LIMIT = 50;

    private final BranchStore branches;
    private final Map<String, UndoStack> stacks = new ConcurrentHashMap<>();

    public UndoService(BranchStore branches) {
        this.branches = branches;
    }

    private UndoStack stack(long repoId, String branch) {
        return stacks.computeIfAbsent(repoId + ":" + branch, k -> new UndoStack(LIMIT));
    }

    public void record(long repoId, String branch, HeadMoveCommand.Kind kind, String before, String after) {
        stack(repoId, branch).push(new HeadMoveCommand(kind, before, after,
                (expected, next) -> branches.moveHead(repoId, branch, expected, next)));
    }

    public String undo(long repoId, String branch) {
        UndoStack s = stack(repoId, branch);
        if (!s.canUndo()) {
            throw new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_UNDO", "Nothing to undo on " + branch);
        }
        try {
            return s.undo().description();
        } catch (IllegalStateException moved) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED", moved.getMessage());
        }
    }

    public List<String> history(long repoId, String branch) {
        return stack(repoId, branch).entries();
    }

    /** Call when a branch is deleted so a recreated branch doesn't inherit old entries. */
    public void forget(long repoId, String branch) {
        stacks.remove(repoId + ":" + branch);
    }
}

package in.nitw.minigit.branch;

import in.nitw.minigit.commit.WorktreeService;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.undo.UndoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** U5: list, create, check out and delete branches. Heads live in the branches table. */
@Service
public class BranchService {

    public record BranchView(String name, String headCommitHash, boolean isProtected) {}

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final BranchStore branches;
    private final RepoObjectStores stores;
    private final UndoService undo;
    private final WorktreeService worktree;

    public BranchService(BranchStore branches, RepoObjectStores stores, UndoService undo, WorktreeService worktree) {
        this.branches = branches;
        this.stores = stores;
        this.undo = undo;
        this.worktree = worktree;
    }

    public List<BranchView> list(long repoId) {
        return branches.list(repoId).stream()
                .map(b -> new BranchView(b.name(), b.headCommitHash(), b.isProtected()))
                .toList();
    }

    /** {@code from} is either an existing branch name or a full 64-char commit hash. */
    public BranchView create(long repoId, String name, String from) {
        BranchNames.check(name);
        if (branches.find(repoId, name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN", "Branch already exists: " + name);
        }
        if (branches.isInRecycleBin(repoId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN",
                    "A branch named " + name + " is in the recycle bin. Restore or delete it first.");
        }
        String start = resolveStart(repoId, from);
        branches.create(repoId, name, start);
        return new BranchView(name, start, false);
    }

    /**
     * Makes the user's working copy match the branch head exactly (stale files removed) and records which
     * branch it is on. Refuses with 409 WORKTREE_DIRTY while there are uncommitted changes (U3 owns that state).
     */
    public String checkout(long repoId, long userId, String name) {
        require(repoId, name);
        return worktree.checkout(repoId, userId, name, name);
    }

    public void delete(long repoId, long userId, String name) {
        BranchStore.Branch b = require(repoId, name);
        if (b.isProtected() || "main".equals(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_PROTECTED", name + " is protected");
        }
        branches.softDelete(repoId, name, userId);
        undo.forget(repoId, name);
    }

    public String undo(long repoId, String name) {
        require(repoId, name);
        return undo.undo(repoId, name);
    }

    public List<String> undoHistory(long repoId, String name) {
        require(repoId, name);
        return undo.history(repoId, name);
    }

    private String resolveStart(long repoId, String from) {
        if (from == null || from.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "from is required");
        }
        if (HASH.matcher(from).matches()) {
            try {
                stores.history(repoId).getCommit(from);
                return from;
            } catch (RuntimeException missing) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No commit " + from);
            }
        }
        return require(repoId, from).headCommitHash();
    }

    private BranchStore.Branch require(long repoId, String name) {
        return branches.find(repoId, name)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No branch " + name));
    }
}

package in.nitw.minigit.commit;

import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.branch.WorktreeSync;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.storage.RepoObjectStores;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** U3: the per-user server-side working copy of a repository. */
@Service
public class WorktreeService {

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;   // placeholder until V-01..V-09 are confirmed

    public record State(String branch, String baseCommit, String headCommit, boolean headMoved,
                        List<WorkingTree.Change> changes, List<WorkingTree.FileInfo> files) {}

    private final RepoObjectStores stores;
    private final BranchStore branches;
    private final WorkingCopyRows rows;

    public WorktreeService(RepoObjectStores stores, BranchStore branches, WorkingCopyRows rows) {
        this.stores = stores;
        this.branches = branches;
        this.rows = rows;
    }

    Path dir(long repoId, long userId) {
        return stores.worktree(repoId, userId);
    }

    /** The user's working copy row; the first time, it is a checkout of the repo's default branch. */
    @Transactional
    public WorkingCopyRows.Row ensure(long repoId, long userId, String defaultBranch) {
        return rows.find(userId, repoId).orElseGet(() -> {
            checkoutUnchecked(repoId, userId, defaultBranch);
            return rows.find(userId, repoId).orElseThrow();
        });
    }

    public State state(long repoId, long userId, String defaultBranch) {
        WorkingCopyRows.Row wc = ensure(repoId, userId, defaultBranch);
        BranchStore.Branch b = branches.find(repoId, wc.branchName()).orElseThrow();
        Map<String, byte[]> work = WorkingTree.read(dir(repoId, userId));
        Map<String, byte[]> base = stores.history(repoId).snapshot(wc.baseCommitHash());
        return new State(wc.branchName(), wc.baseCommitHash(), b.headCommitHash(),
                !b.headCommitHash().equals(wc.baseCommitHash()),
                WorkingTree.changes(base, work), WorkingTree.list(work));
    }

    public boolean isClean(long repoId, long userId, WorkingCopyRows.Row wc) {
        return WorkingTree.changes(stores.history(repoId).snapshot(wc.baseCommitHash()),
                WorkingTree.read(dir(repoId, userId))).isEmpty();
    }

    /** Switch branches. Refuses while there are uncommitted changes (commit or discard first). */
    @Transactional
    public String checkout(long repoId, long userId, String defaultBranch, String branch) {
        WorkingCopyRows.Row current = ensure(repoId, userId, defaultBranch);
        if (!isClean(repoId, userId, current)) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKTREE_DIRTY",
                    "You have uncommitted changes. Commit or discard them before switching branches.");
        }
        return checkoutUnchecked(repoId, userId, branch);
    }

    private String checkoutUnchecked(long repoId, long userId, String branch) {
        BranchStore.Branch b = branches.find(repoId, branch)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No branch " + branch));
        WorktreeSync.sync(stores.history(repoId).snapshot(b.headCommitHash()), dir(repoId, userId));
        rows.save(userId, repoId, b.id(), b.headCommitHash());
        return b.headCommitHash();
    }

    /** Throw away every local change and move to the current head of the branch (also how you recover from a moved head). */
    @Transactional
    public void discard(long repoId, long userId, String defaultBranch) {
        WorkingCopyRows.Row wc = ensure(repoId, userId, defaultBranch);
        checkoutUnchecked(repoId, userId, wc.branchName());
    }

    public void putFile(long repoId, long userId, String defaultBranch, String path, byte[] data) {
        ensure(repoId, userId, defaultBranch);
        if (data.length > MAX_FILE_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Files can be at most " + (MAX_FILE_BYTES / 1024 / 1024) + " MB");
        }
        WorkingTree.write(dir(repoId, userId), path, data);
    }

    public byte[] readFile(long repoId, long userId, String defaultBranch, String path) {
        ensure(repoId, userId, defaultBranch);
        byte[] data = WorkingTree.readFile(dir(repoId, userId), path);
        if (data == null) throw ApiException.notFound("File");
        return data;
    }

    public void deleteFile(long repoId, long userId, String defaultBranch, String path) {
        ensure(repoId, userId, defaultBranch);
        if (!WorkingTree.delete(dir(repoId, userId), path)) throw ApiException.notFound("File");
    }
}

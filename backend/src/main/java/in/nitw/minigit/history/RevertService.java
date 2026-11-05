package in.nitw.minigit.history;

import in.nitw.minigit.branch.BranchProtection;
import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.branch.CommitRows;
import in.nitw.minigit.commit.CommitService;
import in.nitw.minigit.commit.WorkingCopyRows;
import in.nitw.minigit.commit.WorktreeService;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.merge.MergeEngine;
import in.nitw.minigit.merge.RevertEngine;
import in.nitw.minigit.storage.Commit;
import in.nitw.minigit.storage.HistoryService;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.storage.StoredCommit;
import in.nitw.minigit.undo.HeadMoveCommand;
import in.nitw.minigit.undo.UndoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Revert Commit: adds a NEW commit on a branch that undoes an earlier commit's changes. History is never rewritten,
 * and the revert can itself be undone (it is recorded on the branch's undo stack like any commit).
 * Refuses the first commit, merge commits, commits that aren't part of the branch, a branch that requires
 * approval, and any revert that would conflict with later changes (nothing is changed in that case).
 */
@Service
public class RevertService {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final RepoObjectStores stores;
    private final BranchStore branches;
    private final CommitRows commitRows;
    private final WorktreeService worktree;
    private final UndoService undo;
    private final BranchProtection protection;

    public RevertService(RepoObjectStores stores, BranchStore branches, CommitRows commitRows,
                         WorktreeService worktree, UndoService undo, BranchProtection protection) {
        this.stores = stores;
        this.branches = branches;
        this.commitRows = commitRows;
        this.worktree = worktree;
        this.undo = undo;
        this.protection = protection;
    }

    @Transactional
    public CommitService.CommitView revert(long repoId, long userId, String username, String defaultBranch,
                                           String branch, String commitHash) {
        if (branch == null || branch.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Pick the branch to revert on");
        }
        if (commitHash == null || !HASH.matcher(commitHash).matches()) throw ApiException.notFound("Commit");
        BranchStore.Branch b = branches.find(repoId, branch)
                .orElseThrow(() -> ApiException.notFound("Branch " + branch));
        if (protection.isRequired(repoId, branch)) {
            throw BranchProtection.direct(branch, "revert a commit on");
        }

        HistoryService history = stores.history(repoId);
        Commit target;
        try {
            target = history.getCommit(commitHash);
        } catch (RuntimeException missing) {
            throw ApiException.notFound("Commit");
        }
        if (target.getParentHashes().isEmpty()) {
            throw ApiException.badRequest("CANNOT_REVERT", "The first commit has nothing to undo");
        }
        if (target.getParentHashes().size() > 1) {
            throw ApiException.badRequest("CANNOT_REVERT", "Merge commits can't be reverted");
        }
        String head = b.headCommitHash();
        if (!new MergeEngine(history).isAncestor(commitHash, head)) {
            throw ApiException.badRequest("NOT_IN_BRANCH", "That commit is not part of " + branch + "'s history");
        }

        WorkingCopyRows.Row wc = worktree.ensure(repoId, userId, defaultBranch);
        boolean mine = wc.branchName().equals(branch);
        if (mine && !worktree.isClean(repoId, userId, wc)) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKTREE_DIRTY",
                    "You have uncommitted changes on " + branch + ". Commit or discard them first.");
        }

        RevertEngine.Result r = RevertEngine.apply(history.snapshot(commitHash),
                history.snapshot(target.getParentHashes().get(0)), history.snapshot(head));
        if (!r.conflicts().isEmpty()) {
            throw ApiException.conflict("REVERT_CONFLICT", "Can't revert cleanly: later changes touch "
                    + String.join(", ", r.conflicts().keySet()) + ". Nothing was changed.");
        }
        String tree = stores.buildTree(repoId, r.files());
        if (tree.equals(history.getCommit(head).getTreeHash())) {
            throw ApiException.badRequest("NOTHING_TO_REVERT",
                    "This commit's changes are already gone from " + branch);
        }

        String first = target.getMessage().strip().split("\n", 2)[0];
        if (first.length() > 200) first = first.substring(0, 200);
        String message = "Revert \"" + first + "\"\n\nThis reverts commit " + commitHash + ".";
        StoredCommit c = stores.writeCommit(repoId, tree, List.of(head), username, message);
        commitRows.insert(repoId, c.hash(), tree, userId, message, c.createdAt(), List.of(head));
        if (!branches.moveHead(repoId, branch, head, c.hash())) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED",
                    branch + " moved since you started. Reload and try again.");
        }
        undo.record(repoId, branch, HeadMoveCommand.Kind.COMMIT, head, c.hash());
        if (mine) worktree.discard(repoId, userId, defaultBranch);
        return CommitService.CommitView.of(c.hash(), c.commit());
    }
}

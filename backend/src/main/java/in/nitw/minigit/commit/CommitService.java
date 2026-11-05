package in.nitw.minigit.commit;

import in.nitw.minigit.branch.BranchNames;
import in.nitw.minigit.branch.BranchProtection;
import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.branch.CommitRows;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.storage.Commit;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.storage.StoredCommit;
import in.nitw.minigit.undo.HeadMoveCommand;
import in.nitw.minigit.undo.UndoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * U3: turn the working copy into a commit.
 * The branch head is moved with compare-and-set. If it moved since checkout the commit is refused
 * (409 BRANCH_HEAD_MOVED) unless a new branch name is given, which starts from the commit you based your work on.
 */
@Service
public class CommitService {

    public record CommitView(String hash, String treeHash, List<String> parents, String author,
                             String message, Instant createdAt) {
        public static CommitView of(String hash, Commit c) {
            return new CommitView(hash, c.getTreeHash(), c.getParentHashes(), c.getAuthor(), c.getMessage(), c.getTime());
        }
    }

    private final RepoObjectStores stores;
    private final BranchStore branches;
    private final CommitRows commitRows;
    private final WorkingCopyRows workingCopies;
    private final WorktreeService worktree;
    private final UndoService undo;
    private final BranchProtection protection;

    public CommitService(RepoObjectStores stores, BranchStore branches, CommitRows commitRows,
                         WorkingCopyRows workingCopies, WorktreeService worktree, UndoService undo,
                         BranchProtection protection) {
        this.stores = stores;
        this.branches = branches;
        this.commitRows = commitRows;
        this.workingCopies = workingCopies;
        this.worktree = worktree;
        this.undo = undo;
        this.protection = protection;
    }

    @Transactional
    public CommitView commit(long repoId, long userId, String username, String defaultBranch,
                             String message, String newBranch) {
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Write a commit message");
        }
        if (message.length() > 1000) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Commit messages can be at most 1000 characters");
        }
        String msg = message.strip();

        WorkingCopyRows.Row wc = worktree.ensure(repoId, userId, defaultBranch);
        BranchStore.Branch onBranch = branches.find(repoId, wc.branchName())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No branch " + wc.branchName()));
        String base = wc.baseCommitHash();
        boolean moved = !onBranch.headCommitHash().equals(base);

        boolean toNewBranch = newBranch != null && !newBranch.isBlank();
        if (!toNewBranch && protection.isRequired(repoId, onBranch.name())) {
            throw BranchProtection.direct(onBranch.name(), "commit to");
        }
        if (moved && !toNewBranch) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED",
                    "The branch moved since you started. Discard your changes or commit to a new branch.");
        }
        if (toNewBranch) {
            BranchNames.check(newBranch);
            if (branches.find(repoId, newBranch).isPresent() || branches.isInRecycleBin(repoId, newBranch)) {
                throw ApiException.conflict("NAME_TAKEN", "A branch named " + newBranch + " already exists");
            }
        }

        Map<String, byte[]> files = WorkingTree.read(worktree.dir(repoId, userId));
        String tree = stores.buildTree(repoId, files);   // normalises paths, rejects clashes
        if (tree.equals(stores.history(repoId).getCommit(base).getTreeHash())) {
            throw ApiException.badRequest("NOTHING_TO_COMMIT", "There are no changes to commit");
        }

        StoredCommit c = stores.writeCommit(repoId, tree, List.of(base), username, msg);
        // Row first (a branch head references commits), then move the head. A lost race throws and rolls the row back.
        commitRows.insert(repoId, c.hash(), tree, userId, msg, c.createdAt(), List.of(base));

        long branchId;
        String branchName;
        if (toNewBranch) {
            branches.create(repoId, newBranch, base);
            branchId = branches.find(repoId, newBranch).orElseThrow().id();
            branchName = newBranch;
        } else {
            branchId = onBranch.id();
            branchName = onBranch.name();
        }
        if (!branches.moveHead(repoId, branchName, base, c.hash())) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED",
                    "The branch moved since you started. Discard your changes or commit to a new branch.");
        }
        undo.record(repoId, branchName, HeadMoveCommand.Kind.COMMIT, base, c.hash());
        workingCopies.save(userId, repoId, branchId, c.hash());
        return CommitView.of(c.hash(), c.commit());
    }
}

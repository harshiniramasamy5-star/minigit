package in.nitw.minigit.merge;

import in.nitw.minigit.branch.BranchProtection;
import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.branch.CommitRows;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.storage.Blob;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.storage.StoredCommit;
import in.nitw.minigit.undo.HeadMoveCommand;
import in.nitw.minigit.undo.UndoService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * U6/U7: merge {@code source} into {@code target} (SRS: up-to-date / fast-forward / three-way,
 * conflicts resolved in a merge session that can be aborted).
 *
 * The target head is re-checked with compare-and-set before it moves, so a merge never
 * overwrites a commit someone else pushed in the meantime (409 BRANCH_HEAD_MOVED).
 *
 * Sessions waiting for conflict resolution live in the database ({@link MergeSessionStore}), so they
 * survive a restart. A session expires one hour after it was started.
 */
@Service
public class MergeService {

    public record ConflictView(String path, FileConflict.Kind kind, boolean binary,
                               String base, String ours, String theirs) {
        static ConflictView of(FileConflict c) {
            return new ConflictView(c.path(), c.kind(), c.binary(), c.baseText(), c.oursText(), c.theirsText());
        }
    }

    public record SessionView(String sessionId, String sourceBranch, String targetBranch, String baseHash,
                              List<ConflictView> conflicts) {
        static SessionView of(MergeSession s) {
            return new SessionView(s.id(), s.sourceBranch(), s.targetBranch(), s.baseHash(),
                    s.conflicts().values().stream().map(ConflictView::of).toList());
        }
    }

    /** status: UP_TO_DATE | FAST_FORWARD | MERGED | CONFLICTS. session is set only for CONFLICTS. */
    public record MergeResult(String status, String commitHash, SessionView session) {}

    private final RepoObjectStores stores;
    private final BranchStore branches;
    private final CommitRows commitRows;
    private final UndoService undo;
    private final ApplicationEventPublisher events;
    private final MergeSessionStore sessions;
    private final BranchProtection protection;

    public MergeService(RepoObjectStores stores, BranchStore branches, CommitRows commitRows, UndoService undo,
                        ApplicationEventPublisher events, MergeSessionStore sessions,
                        BranchProtection protection) {
        this.sessions = sessions;
        this.protection = protection;
        this.stores = stores;
        this.branches = branches;
        this.commitRows = commitRows;
        this.undo = undo;
        this.events = events;
    }

    @Transactional
    public MergeResult merge(long repoId, long userId, String authorName,
                             String source, String target, String expectedTargetHead, String message) {
        if (source == null || target == null || source.equals(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Pick two different branches");
        }
        BranchStore.Branch targetBranch = branch(repoId, target);
        BranchStore.Branch sourceBranch = branch(repoId, source);
        String ours = targetBranch.headCommitHash();
        String theirs = sourceBranch.headCommitHash();
        if (expectedTargetHead != null && !expectedTargetHead.equals(ours)) {
            throw moved(target);
        }
        sessions.expireOld();
        if (sessions.hasOpen(repoId, targetBranch.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "MERGE_IN_PROGRESS",
                    "A merge into " + target + " is waiting for conflict resolution; finish or abort it first");
        }

        MergeEngine.Plan plan;
        try {
            plan = new MergeEngine(stores.history(repoId)).plan(ours, theirs);
        } catch (IllegalStateException unrelated) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", unrelated.getMessage());
        }

        switch (plan.outcome()) {
            case UP_TO_DATE:
                return new MergeResult("UP_TO_DATE", ours, null);
            case FAST_FORWARD:
                if (!branches.moveHead(repoId, target, ours, theirs)) throw moved(target);
                undo.record(repoId, target, HeadMoveCommand.Kind.MERGE, ours, theirs);
                events.publishEvent(new BranchesMerged(repoId, source, target, theirs));
                return new MergeResult("FAST_FORWARD", theirs, null);
            default:
                break;
        }

        if (!plan.conflicts().isEmpty()) {
            long id = sessions.insert(repoId, userId, sourceBranch.id(), targetBranch.id(),
                    plan.base(), ours, theirs, plan.conflicts());
            MergeSession session = new MergeSession(Long.toString(id), repoId, userId, source, target,
                    ours, theirs, plan.base(), plan.clean(), plan.conflicts());
            return new MergeResult("CONFLICTS", null, SessionView.of(session));
        }
        MergeSession session = new MergeSession("none", repoId, userId, source, target,
                ours, theirs, plan.base(), plan.clean(), plan.conflicts());
        String hash = commitMerge(session, userId, authorName,
                message != null ? message : "Merge " + source + " into " + target);
        return new MergeResult("MERGED", hash, null);
    }

    public SessionView session(long repoId, String sessionId) {
        return SessionView.of(require(repoId, sessionId));
    }

    public SessionView resolve(long repoId, String sessionId, String path, String choice, String content) {
        MergeSession s = require(repoId, sessionId);
        try {
            s.resolve(path, choice, content);
        } catch (IllegalArgumentException bad) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", bad.getMessage());
        }
        save(s, path, choice);
        return SessionView.of(s);
    }

    @Transactional
    public String finish(long repoId, long userId, String authorName, String sessionId, String message) {
        MergeSession s = require(repoId, sessionId);
        if (protection.isRequired(repoId, s.targetBranch())
                && !protection.hasApprovedOpenPr(repoId, s.sourceBranch(), s.targetBranch())) {
            throw BranchProtection.needsApproval(s.targetBranch());
        }
        if (!s.unresolved().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "MERGE_CONFLICT", "Unresolved files: " + s.unresolved());
        }
        String hash = commitMerge(s, userId, authorName,
                message != null ? message : "Merge " + s.sourceBranch() + " into " + s.targetBranch());
        sessions.close(Long.parseLong(sessionId), "COMPLETED");
        return hash;
    }

    public void abort(long repoId, String sessionId) {
        require(repoId, sessionId);
        sessions.close(Long.parseLong(sessionId), "ABORTED");
    }

    private String commitMerge(MergeSession s, long userId, String authorName, String message) {
        String tree = stores.buildTree(s.repoId(), s.finalFiles());
        List<String> parents = List.of(s.oursHash(), s.theirsHash());
        StoredCommit c = stores.writeCommit(s.repoId(), tree, parents, authorName, message);
        // Row first (branches.head_commit_hash may reference commits), then move the head.
        // If the head moved, the exception rolls the row back; the orphan object is harmless.
        commitRows.insert(s.repoId(), c.hash(), tree, userId, message, c.createdAt(), parents);
        if (!branches.moveHead(s.repoId(), s.targetBranch(), s.oursHash(), c.hash())) {
            throw moved(s.targetBranch());
        }
        undo.record(s.repoId(), s.targetBranch(), HeadMoveCommand.Kind.MERGE, s.oursHash(), c.hash());
        events.publishEvent(new BranchesMerged(s.repoId(), s.sourceBranch(), s.targetBranch(), c.hash()));
        return c.hash();
    }

    private BranchStore.Branch branch(long repoId, String name) {
        return branches.find(repoId, name)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No branch " + name));
    }

    /** Stores the choice just applied to one file. ours/theirs are kept as such, everything else as MANUAL. */
    private void save(MergeSession s, String path, String choice) {
        String c = choice.toLowerCase(Locale.ROOT);
        long id = Long.parseLong(s.id());
        if (c.equals("ours")) {
            sessions.saveResolution(id, path, "OURS", null);
        } else if (c.equals("theirs")) {
            sessions.saveResolution(id, path, "THEIRS", null);
        } else {
            byte[] value = s.resolvedValue(path);
            String blob = value == null ? null : stores.objects(s.repoId()).write(new Blob(value));
            sessions.saveResolution(id, path, "MANUAL", blob);   // null blob = the file is deleted
        }
    }

    /** Loads an open session: re-plans the merge from the stored hashes, then replays the saved choices. */
    private MergeSession require(long repoId, String sessionId) {
        long id;
        try {
            id = Long.parseLong(sessionId);
        } catch (NumberFormatException e) {
            throw noSession();
        }
        MergeSessionStore.Row row = sessions.findOpen(repoId, id).orElseThrow(MergeService::noSession);
        MergeEngine.Plan plan = new MergeEngine(stores.history(repoId)).plan(row.oursHash(), row.theirsHash());
        MergeSession s = new MergeSession(Long.toString(row.id()), repoId, row.userId(), row.sourceBranch(),
                row.targetBranch(), row.oursHash(), row.theirsHash(), row.baseHash(),
                plan.clean(), plan.conflicts(), row.createdAt());
        for (MergeSessionStore.Stored r : sessions.conflicts(id)) {
            switch (r.resolution()) {
                case "OURS" -> s.resolve(r.path(), "ours", null);
                case "THEIRS" -> s.resolve(r.path(), "theirs", null);
                case "MANUAL" -> s.resolveRaw(r.path(), r.blobHash() == null ? null
                        : ((Blob) stores.objects(repoId).read(r.blobHash())).getContent());
                default -> { }   // UNRESOLVED
            }
        }
        return s;
    }

    private static ApiException noSession() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such merge session");
    }

    private static ApiException moved(String branch) {
        return new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED",
                branch + " moved since you started. Reload and try again.");
    }
}

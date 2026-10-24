package in.nitw.minigit.merge;

import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.branch.CommitRows;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.storage.StoredCommit;
import in.nitw.minigit.undo.HeadMoveCommand;
import in.nitw.minigit.undo.UndoService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * U6/U7: merge {@code source} into {@code target} (SRS: up-to-date / fast-forward / three-way,
 * conflicts resolved in a merge session that can be aborted).
 *
 * The target head is re-checked with compare-and-set before it moves, so a merge never
 * overwrites a commit someone else pushed in the meantime (409 BRANCH_HEAD_MOVED).
 */
@Service
public class MergeService {

    private static final Duration SESSION_TTL = Duration.ofHours(1);

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
    private final Map<String, MergeSession> sessions = new ConcurrentHashMap<>();

    public MergeService(RepoObjectStores stores, BranchStore branches, CommitRows commitRows, UndoService undo,
                        ApplicationEventPublisher events) {
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
        String ours = head(repoId, target);
        String theirs = head(repoId, source);
        if (expectedTargetHead != null && !expectedTargetHead.equals(ours)) {
            throw moved(target);
        }
        dropExpiredSessions();
        if (sessions.values().stream().anyMatch(s -> s.repoId() == repoId && s.targetBranch().equals(target))) {
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

        MergeSession session = new MergeSession(UUID.randomUUID().toString(), repoId, userId, source, target,
                ours, theirs, plan.base(), plan.clean(), plan.conflicts());
        if (!plan.conflicts().isEmpty()) {
            sessions.put(session.id(), session);
            return new MergeResult("CONFLICTS", null, SessionView.of(session));
        }
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
        return SessionView.of(s);
    }

    @Transactional
    public String finish(long repoId, long userId, String authorName, String sessionId, String message) {
        MergeSession s = require(repoId, sessionId);
        if (!s.unresolved().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "MERGE_CONFLICT", "Unresolved files: " + s.unresolved());
        }
        String hash = commitMerge(s, userId, authorName,
                message != null ? message : "Merge " + s.sourceBranch() + " into " + s.targetBranch());
        sessions.remove(sessionId);
        return hash;
    }

    public void abort(long repoId, String sessionId) {
        require(repoId, sessionId);
        sessions.remove(sessionId);
    }

    private String commitMerge(MergeSession s, long userId, String authorName, String message) {
        String tree = stores.buildTree(s.repoId(), s.finalFiles());
        List<String> parents = List.of(s.oursHash(), s.theirsHash());
        StoredCommit c = stores.writeCommit(s.repoId(), tree, parents, authorName, message);
        // Row first (branches.head_commit_hash may reference commits), then move the head.
        // If the head moved, the exception rolls the row back; the orphan object is harmless.
        commitRows.insert(s.repoId(), c.hash(), tree, userId, message, c.createdAt(), parents);
        if (!branches.moveHead(s.repoId(), s.targetBranch(), s.oursHash(), c.hash())) {
            sessions.remove(s.id());
            throw moved(s.targetBranch());
        }
        undo.record(s.repoId(), s.targetBranch(), HeadMoveCommand.Kind.MERGE, s.oursHash(), c.hash());
        events.publishEvent(new BranchesMerged(s.repoId(), s.sourceBranch(), s.targetBranch(), c.hash()));
        return c.hash();
    }

    private String head(long repoId, String branch) {
        return branches.find(repoId, branch)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No branch " + branch))
                .headCommitHash();
    }

    private MergeSession require(long repoId, String sessionId) {
        MergeSession s = sessions.get(sessionId);
        if (s == null || s.repoId() != repoId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such merge session");
        }
        return s;
    }

    private void dropExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        sessions.values().removeIf(s -> s.startedAt().isBefore(cutoff));
    }

    private static ApiException moved(String branch) {
        return new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED",
                branch + " moved since you started. Reload and try again.");
    }
}

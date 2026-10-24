package in.nitw.minigit.history;

import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.branch.CommitRows;
import in.nitw.minigit.commit.CommitService.CommitView;
import in.nitw.minigit.commit.WorkingCopyRows;
import in.nitw.minigit.commit.WorkingTree;
import in.nitw.minigit.commit.WorktreeService;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.common.PageResponse;
import in.nitw.minigit.common.PathValidator;
import in.nitw.minigit.storage.Commit;
import in.nitw.minigit.storage.LogEntry;
import in.nitw.minigit.storage.RepoObjectStores;
import in.nitw.minigit.storage.StoredCommit;
import in.nitw.minigit.storage.diff.DiffLine;
import in.nitw.minigit.storage.diff.MyersDiff;
import in.nitw.minigit.undo.HeadMoveCommand;
import in.nitw.minigit.undo.UndoService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** U4: commit history, browsing a commit, diff, restore, and the data for the commit graph. */
@Service
public class RepoHistory {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final int MAX_DIFF_BYTES = 1_000_000;

    public record CommitDetail(CommitView commit, List<WorkingTree.Change> files) {}

    public record FileDiffView(String path, String status, boolean binary, List<DiffLine> lines) {}

    public record GraphCommit(String hash, List<String> parents, String message, String author, Instant createdAt) {}

    public record GraphBranch(String name, String head) {}

    public record BranchInfo(String name, String head, boolean isProtected, boolean isDefault) {}

    public record Graph(List<GraphCommit> commits, List<GraphBranch> branches) {}

    private final RepoObjectStores stores;
    private final BranchStore branches;
    private final CommitRows commitRows;
    private final JdbcTemplate jdbc;
    private final WorktreeService worktree;
    private final UndoService undo;

    public RepoHistory(RepoObjectStores stores, BranchStore branches, CommitRows commitRows, JdbcTemplate jdbc,
                       WorktreeService worktree, UndoService undo) {
        this.stores = stores;
        this.branches = branches;
        this.commitRows = commitRows;
        this.jdbc = jdbc;
        this.worktree = worktree;
        this.undo = undo;
    }

    /** A branch name or a full commit hash that belongs to this repo. Blank = the default branch. */
    public String resolve(long repoId, String ref, String defaultBranch) {
        String r = (ref == null || ref.isBlank()) ? defaultBranch : ref.strip();
        if (HASH.matcher(r).matches()) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM commits WHERE repo_id = ? AND hash = ?", Long.class, repoId, r);
            if (n == null || n == 0) throw ApiException.notFound("Commit");
            return r;
        }
        return branches.find(repoId, r)
                .orElseThrow(() -> ApiException.notFound("Branch " + r)).headCommitHash();
    }

    public PageResponse<CommitView> log(long repoId, String ref, String defaultBranch, int page, int size) {
        List<LogEntry> all = stores.history(repoId).log(resolve(repoId, ref, defaultBranch));
        long from = Math.min(PageResponse.offset(page, size), all.size());
        int to = (int) Math.min(from + size, all.size());
        List<CommitView> items = all.subList((int) from, to).stream()
                .map(e -> CommitView.of(e.hash(), e.commit())).toList();
        return new PageResponse<>(items, page, size, all.size());
    }

    public CommitDetail detail(long repoId, String hash) {
        if (hash == null || !HASH.matcher(hash).matches()) throw ApiException.notFound("Commit");
        String h = resolve(repoId, hash, null);
        Commit c = stores.history(repoId).getCommit(h);
        Map<String, byte[]> now = stores.history(repoId).snapshot(h);
        Map<String, byte[]> before = c.getParentHashes().isEmpty()
                ? Map.of() : stores.history(repoId).snapshot(c.getParentHashes().get(0));
        return new CommitDetail(CommitView.of(h, c), WorkingTree.changes(before, now));
    }

    /** from may be blank (compare against an empty project). */
    public List<FileDiffView> diff(long repoId, String from, String to, String defaultBranch) {
        Map<String, byte[]> a = (from == null || from.isBlank())
                ? Map.of() : stores.history(repoId).snapshot(resolve(repoId, from, defaultBranch));
        Map<String, byte[]> b = stores.history(repoId).snapshot(resolve(repoId, to, defaultBranch));
        MyersDiff algorithm = new MyersDiff();
        List<FileDiffView> out = new ArrayList<>();
        for (String path : new TreeSet<>(union(a, b))) {
            byte[] before = a.get(path);
            byte[] after = b.get(path);
            String status = before == null ? "ADDED" : after == null ? "DELETED" : null;
            if (status == null) {
                if (java.util.Arrays.equals(before, after)) continue;
                status = "MODIFIED";
            }
            boolean showable = (before == null || (before.length <= MAX_DIFF_BYTES && TextCheck.isText(before)))
                    && (after == null || (after.length <= MAX_DIFF_BYTES && TextCheck.isText(after)));
            if (!showable) {
                out.add(new FileDiffView(path, status, true, List.of()));
            } else {
                out.add(new FileDiffView(path, status, false, algorithm.diff(text(before), text(after))));
            }
        }
        return out;
    }

    public List<TreeListing.Entry> tree(long repoId, String ref, String path, String defaultBranch) {
        String dir = (path == null || path.isBlank()) ? "" : PathValidator.normalise(path);
        List<TreeListing.Entry> entries = TreeListing.list(
                stores.history(repoId).snapshot(resolve(repoId, ref, defaultBranch)), dir);
        if (entries == null) throw ApiException.notFound("Folder");
        return entries;
    }

    /** @return the file bytes at a ref, 404 if absent */
    public byte[] blob(long repoId, String ref, String path, String defaultBranch) {
        if (path == null || path.isBlank()) throw ApiException.badRequest("VALIDATION_FAILED", "path is required");
        String clean = PathValidator.normalise(path);
        byte[] data = stores.history(repoId).snapshot(resolve(repoId, ref, defaultBranch)).get(clean);
        if (data == null) throw ApiException.notFound("File");
        return data;
    }

    /**
     * Restore a whole commit, or one file of it, as a NEW commit on the branch (history is never rewritten;
     * undo can step back). If your working copy is on that branch it must be clean, and is refreshed afterwards.
     */
    @Transactional
    public CommitView restore(long repoId, long userId, String username, String defaultBranch,
                              String branch, String commit, String path) {
        if (branch == null || branch.isBlank() || commit == null || !HASH.matcher(commit).matches()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "branch and a full commit hash are required");
        }
        BranchStore.Branch b = branches.find(repoId, branch)
                .orElseThrow(() -> ApiException.notFound("Branch " + branch));
        String target = resolve(repoId, commit, defaultBranch);

        WorkingCopyRows.Row wc = worktree.ensure(repoId, userId, defaultBranch);
        boolean mine = wc.branchName().equals(branch);
        if (mine && !worktree.isClean(repoId, userId, wc)) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKTREE_DIRTY",
                    "You have uncommitted changes on " + branch + ". Commit or discard them first.");
        }

        String head = b.headCommitHash();
        Map<String, byte[]> files = RestorePlan.apply(stores.history(repoId).snapshot(head),
                stores.history(repoId).snapshot(target), path);
        String tree = stores.buildTree(repoId, files);
        String what = (path == null || path.isBlank()) ? "all files" : PathValidator.normalise(path);
        String message = "Restore " + what + " from " + target.substring(0, 7);
        StoredCommit c = stores.writeCommit(repoId, tree, List.of(head), username, message);
        commitRows.insert(repoId, c.hash(), tree, userId, message, c.createdAt(), List.of(head));
        if (!branches.moveHead(repoId, branch, head, c.hash())) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED",
                    branch + " moved since you started. Reload and try again.");
        }
        undo.record(repoId, branch, HeadMoveCommand.Kind.RESTORE, head, c.hash());
        if (mine) worktree.discard(repoId, userId, defaultBranch);
        return CommitView.of(c.hash(), c.commit());
    }

    /** Branches that are not in the recycle bin, default branch first. Readable by anyone who can read the repo. */
    public List<BranchInfo> branchList(long repoId, String defaultBranch) {
        List<BranchInfo> out = new ArrayList<>(branches.list(repoId).stream()
                .map(b -> new BranchInfo(b.name(), b.headCommitHash(), b.isProtected(), b.name().equals(defaultBranch)))
                .toList());
        out.sort(Comparator.comparing((BranchInfo b) -> !b.isDefault()).thenComparing(BranchInfo::name));
        return out;
    }

    /** Commits of every branch, newest first, plus where each branch points (for the graph page). */
    public Graph graph(long repoId, int limit) {
        List<BranchStore.Branch> all = branches.list(repoId);
        Map<String, Commit> seen = new LinkedHashMap<>();
        for (BranchStore.Branch b : all) {
            for (LogEntry e : stores.history(repoId).log(b.headCommitHash())) {
                seen.putIfAbsent(e.hash(), e.commit());
            }
        }
        List<Map.Entry<String, Commit>> sorted = new ArrayList<>(seen.entrySet());
        sorted.sort(Comparator.comparing((Map.Entry<String, Commit> e) -> e.getValue().getTime()).reversed());
        List<GraphCommit> commits = sorted.stream().limit(limit)
                .map(e -> new GraphCommit(e.getKey(), e.getValue().getParentHashes(),
                        e.getValue().getMessage(), e.getValue().getAuthor(), e.getValue().getTime()))
                .toList();
        return new Graph(commits, all.stream().map(b -> new GraphBranch(b.name(), b.headCommitHash())).toList());
    }

    private static java.util.Set<String> union(Map<String, byte[]> a, Map<String, byte[]> b) {
        java.util.Set<String> s = new TreeSet<>(a.keySet());
        s.addAll(b.keySet());
        return s;
    }

    private static String text(byte[] data) {
        return data == null ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }
}

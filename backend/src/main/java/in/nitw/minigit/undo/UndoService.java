package in.nitw.minigit.undo;

import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.common.ApiException;
import in.nitw.minigit.readmodel.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * U10: one undo stack per branch (commit, restore and merge only), stored in the undo_actions table
 * so it survives a restart. Callers are unchanged: U3/U4/U6 call {@link #record} right after they move a head.
 *
 * Undoing moves the head back with a compare-and-set (409 BRANCH_HEAD_MOVED if someone moved it since)
 * and marks the row undone=TRUE. Undone rows stay in the table (the optional redo would use them) but are
 * not shown and not undone again. Only the newest 50 actions per branch are kept.
 */
@Service
public class UndoService {

    private static final int LIMIT = 50;

    private record Action(long id, String type, String before, String after) {}

    private final JdbcTemplate jdbc;
    private final BranchStore branches;
    private final CurrentUser currentUser;

    public UndoService(JdbcTemplate jdbc, BranchStore branches, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.branches = branches;
        this.currentUser = currentUser;
    }

    /** Records an action that has ALREADY happened. The acting user is the logged-in caller. */
    public void record(long repoId, String branch, HeadMoveCommand.Kind kind, String before, String after) {
        record(repoId, branch, kind, before, after, actor(repoId));
    }

    public void record(long repoId, String branch, HeadMoveCommand.Kind kind, String before, String after,
                       long userId) {
        BranchStore.Branch b = branches.find(repoId, branch).orElse(null);
        if (b == null) {
            return;
        }
        jdbc.update("INSERT INTO undo_actions (branch_id, user_id, action_type, head_before_hash, head_after_hash) "
                + "VALUES (?, ?, ?, ?, ?)", b.id(), userId, kind.name(), before, after);
        jdbc.update("DELETE FROM undo_actions WHERE branch_id = ? AND id < "
                + "(SELECT MIN(id) FROM (SELECT id FROM undo_actions WHERE branch_id = ? ORDER BY id DESC LIMIT "
                + LIMIT + ") newest)", b.id(), b.id());
    }

    /** @return a short description of what was undone, e.g. "commit 3fa9c1d" */
    @Transactional
    public String undo(long repoId, String branch) {
        BranchStore.Branch b = requireBranch(repoId, branch);
        List<Action> top = jdbc.query("SELECT id, action_type, head_before_hash, head_after_hash FROM undo_actions "
                        + "WHERE branch_id = ? AND undone = FALSE ORDER BY id DESC LIMIT 1 FOR UPDATE",
                (rs, i) -> new Action(rs.getLong("id"), rs.getString("action_type"),
                        rs.getString("head_before_hash"), rs.getString("head_after_hash")), b.id());
        if (top.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_UNDO", "Nothing to undo on " + branch);
        }
        Action a = top.get(0);
        if (!branches.moveHead(repoId, branch, a.after(), a.before())) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_HEAD_MOVED",
                    "Branch head moved; cannot undo " + describe(a.type(), a.after()));
        }
        jdbc.update("UPDATE undo_actions SET undone = TRUE WHERE id = ?", a.id());
        return describe(a.type(), a.after());
    }

    /** Newest first, same text format as before. */
    public List<String> history(long repoId, String branch) {
        BranchStore.Branch b = requireBranch(repoId, branch);
        return jdbc.query("SELECT action_type, head_after_hash FROM undo_actions "
                        + "WHERE branch_id = ? AND undone = FALSE ORDER BY id DESC",
                (rs, i) -> describe(rs.getString("action_type"), rs.getString("head_after_hash")), b.id());
    }

    /** Drops a branch's undo history (not needed for the recycle bin, which keeps it). */
    public void forget(long repoId, String branch) {
        jdbc.update("DELETE u FROM undo_actions u JOIN branches b ON b.id = u.branch_id "
                + "WHERE b.repo_id = ? AND b.name = ?", repoId, branch);
    }

    private BranchStore.Branch requireBranch(long repoId, String branch) {
        return branches.find(repoId, branch)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No branch " + branch));
    }

    static String describe(String type, String after) {
        return type.toLowerCase() + " " + after.substring(0, Math.min(7, after.length()));
    }

    /** The logged-in user (JWT, or X-Dev-User in open-dev); the repo owner if there is no request (jobs, tests). */
    private long actor(long repoId) {
        String devHeader = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes web) {
            devHeader = web.getRequest().getHeader("X-Dev-User");
        }
        try {
            return currentUser.requireId(devHeader);
        } catch (ApiException noUser) {
            return jdbc.queryForObject("SELECT owner_id FROM repositories WHERE id = ?", Long.class, repoId);
        }
    }
}

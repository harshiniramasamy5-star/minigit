package in.nitw.minigit.branch;

import in.nitw.minigit.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Branch protection rule (V2: branches.require_approval, off by default).
 * When on: no direct commits or direct merges into the branch, and a pull request into it can only be merged
 * after an approval from someone other than its author.
 * Separate from branches.is_protected, which only blocks deleting the branch.
 */
@Component
public class BranchProtection {

    private final JdbcTemplate jdbc;

    public BranchProtection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isRequired(long repoId, String name) {
        if (name == null) return false;
        return jdbc.query("SELECT require_approval FROM branches WHERE repo_id = ? AND name = ? AND deleted_at IS NULL",
                (rs, i) -> rs.getBoolean(1), repoId, name).stream().findFirst().orElse(false);
    }

    public Set<String> requiredNames(long repoId) {
        List<String> names = jdbc.queryForList("SELECT name FROM branches WHERE repo_id = ? "
                + "AND require_approval = TRUE AND deleted_at IS NULL", String.class, repoId);
        return new TreeSet<>(names);
    }

    /** @return false if the branch doesn't exist */
    public boolean set(long repoId, String name, boolean on) {
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM branches WHERE repo_id = ? AND name = ? "
                + "AND deleted_at IS NULL", Long.class, repoId, name);
        if (exists == null || exists == 0) return false;
        jdbc.update("UPDATE branches SET require_approval = ? WHERE repo_id = ? AND name = ? AND deleted_at IS NULL",
                on, repoId, name);
        return true;
    }

    /** True if an open PR source -> target has an approval from someone other than its author. */
    public boolean hasApprovedOpenPr(long repoId, String source, String target) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM pull_requests p JOIN pr_approvals a ON a.pr_id = p.id "
                + "WHERE p.repo_id = ? AND p.source_branch_name = ? AND p.target_branch_name = ? "
                + "AND p.status = 'OPEN' AND a.user_id <> p.author_id", Long.class, repoId, source, target);
        return n != null && n > 0;
    }

    public static ApiException direct(String branch, String action) {
        return new ApiException(HttpStatus.FORBIDDEN, "BRANCH_PROTECTED", branch + " is protected: you can't "
                + action + " it directly. Use a new branch and open a pull request.");
    }

    public static ApiException needsApproval(String target) {
        return new ApiException(HttpStatus.CONFLICT, "APPROVAL_REQUIRED",
                target + " requires at least one approval from someone other than the pull request author");
    }
}

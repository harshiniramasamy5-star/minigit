package in.nitw.minigit.pr;

import in.nitw.minigit.common.ApiException;

import java.util.Locale;
import java.util.Set;

/** The pull request rules from SRS v1.2 that need no database: input checks and who may do what in which state. */
public final class PullRules {

    public static final int MAX_TITLE = 200;
    public static final int MAX_DESCRIPTION = 5000;
    public static final int MAX_COMMENT = 5000;
    private static final Set<String> STATUSES = Set.of("OPEN", "MERGED", "CLOSED");

    private PullRules() {}

    public static void checkNew(String title, String description, String source, String target) {
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Give the pull request a title");
        }
        if (title.strip().length() > MAX_TITLE) {
            throw ApiException.badRequest("VALIDATION_FAILED", "The title can be at most " + MAX_TITLE + " characters");
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw ApiException.badRequest("VALIDATION_FAILED", "The description can be at most " + MAX_DESCRIPTION + " characters");
        }
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Pick a source and a target branch");
        }
        if (source.equals(target)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Source and target branch must differ");
        }
    }

    /** Merged and closed pull requests are final: no comments, approvals, merges or closing. */
    public static void requireOpen(String status) {
        if (!"OPEN".equals(status)) {
            throw ApiException.conflict("PR_NOT_OPEN", "This pull request is already " + status.toLowerCase(Locale.ROOT));
        }
    }

    /** No self-approval (SRS). Whether approval is required to merge depends on the target branch's approval rule (BranchProtection). */
    public static void checkApprover(String author, String reviewer) {
        if (author.equals(reviewer)) {
            throw ApiException.forbidden("Authors cannot approve their own pull request");
        }
    }

    /** @return OPEN / MERGED / CLOSED, or null for "all". No value means OPEN. */
    public static String statusFilter(String raw) {
        if (raw == null || raw.isBlank()) return "OPEN";
        String up = raw.strip().toUpperCase(Locale.ROOT);
        if (up.equals("ALL")) return null;
        if (!STATUSES.contains(up)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "status must be OPEN, MERGED, CLOSED or ALL");
        }
        return up;
    }

    public static String cleanComment(String body) {
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Write a comment first");
        }
        if (body.length() > MAX_COMMENT) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Comments can be at most " + MAX_COMMENT + " characters");
        }
        return body.strip();
    }
}

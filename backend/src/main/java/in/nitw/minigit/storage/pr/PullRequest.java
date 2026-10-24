package in.nitw.minigit.storage.pr;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pull request with its state machine (SRS v1.2, schema V1).
 *
 * OPEN -> MERGED
 * OPEN -> CLOSED
 * MERGED and CLOSED are final.
 *
 * Approval is recorded (pr_approvals), not required to merge. Approving does not change the status.
 */
public class PullRequest {

    private Long id;                       // set from pull_requests.id by the U8 service
    private final String title;
    private final String author;
    private final String sourceBranch;
    private final String targetBranch;
    private PullRequestStatus status;
    private final Set<String> approvals = new LinkedHashSet<>();   // usernames, in approval order
    private final List<PullRequestComment> comments = new ArrayList<>();

    public PullRequest(String title, String author, String sourceBranch, String targetBranch) {
        requireText(title, "title");
        requireText(author, "author");
        requireText(sourceBranch, "source branch");
        requireText(targetBranch, "target branch");
        if (sourceBranch.equals(targetBranch)) {
            throw new IllegalArgumentException("Source and target branch must differ");
        }
        this.title = title;
        this.author = author;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.status = PullRequestStatus.OPEN;
    }

    /** Add a comment. Not allowed once the PR is merged or closed. */
    public void comment(String commenter, String text) {
        if (isFinished()) {
            throw new IllegalStateException("Cannot comment on a " + status + " pull request");
        }
        comments.add(new PullRequestComment(commenter, text, Instant.now()));
    }

    /**
     * Record an approval. The author cannot approve their own PR, and only OPEN PRs can be approved.
     * Approving twice is a no-op (pr_approvals has PRIMARY KEY (pr_id, user_id)).
     *
     * @return true if this is a new approval, false if the reviewer had already approved
     */
    public boolean approve(String reviewer) {
        requireText(reviewer, "reviewer");
        if (reviewer.equals(author)) {
            throw new IllegalStateException("Authors cannot approve their own pull request");
        }
        if (status != PullRequestStatus.OPEN) {
            throw new IllegalStateException("Only OPEN pull requests can be approved, but this one is " + status);
        }
        return approvals.add(reviewer);
    }

    /** Mark as merged. The merge of commits itself is done by the U6 merge service. No approval needed. */
    public void markMerged() {
        if (status != PullRequestStatus.OPEN) {
            throw new IllegalStateException("Only OPEN pull requests can be merged, but this one is " + status);
        }
        this.status = PullRequestStatus.MERGED;
    }

    public void close() {
        if (isFinished()) {
            throw new IllegalStateException("Pull request is already " + status);
        }
        this.status = PullRequestStatus.CLOSED;
    }

    private boolean isFinished() {
        return status == PullRequestStatus.MERGED || status == PullRequestStatus.CLOSED;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pull request " + name + " is required");
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getSourceBranch() { return sourceBranch; }
    public String getTargetBranch() { return targetBranch; }
    public PullRequestStatus getStatus() { return status; }
    public boolean isApproved() { return !approvals.isEmpty(); }
    public Set<String> getApprovals() { return Collections.unmodifiableSet(approvals); }
    public List<PullRequestComment> getComments() { return Collections.unmodifiableList(comments); }
}

package in.nitw.minigit.storage.pr;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pull request with its state machine.
 *
 * OPEN -> APPROVED -> MERGED
 * OPEN or APPROVED -> CLOSED
 * MERGED and CLOSED are final.
 */
public class PullRequest {

    private Long id;                       // set by the database later (Mem 4's JPA setup)
    private final String title;
    private final String author;
    private final String sourceBranch;
    private final String targetBranch;
    private PullRequestStatus status;
    private String approvedBy;
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

    /** Approve the PR. The author cannot approve their own PR (F14). */
    public void approve(String reviewer) {
        requireText(reviewer, "reviewer");
        if (reviewer.equals(author)) {
            throw new IllegalStateException("Authors cannot approve their own pull request");
        }
        if (status != PullRequestStatus.OPEN) {
            throw new IllegalStateException("Only OPEN pull requests can be approved, but this one is " + status);
        }
        this.status = PullRequestStatus.APPROVED;
        this.approvedBy = reviewer;
    }

    /** Mark as merged. The real merge of commits is done by Mem 3's MergeStrategy. */
    public void markMerged() {
        if (status != PullRequestStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED pull requests can be merged, but this one is " + status);
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
    public String getApprovedBy() { return approvedBy; }
    public List<PullRequestComment> getComments() { return Collections.unmodifiableList(comments); }
}
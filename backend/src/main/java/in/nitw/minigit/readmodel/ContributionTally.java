package in.nitw.minigit.readmodel;

import in.nitw.minigit.readmodel.ActivityModels.ActivityBreakdown;

/**
 * Mutable counter for one activity period. State is private (encapsulation); the only way
 * in is {@link #record(ContributionKind)} and the only way out is {@link #breakdown()}.
 */
public final class ContributionTally {

    private int commits;
    private int pullRequests;
    private int reviews;

    public void record(ContributionKind kind) {
        kind.addTo(this);
    }

    void addCommit() { commits++; }
    void addPullRequest() { pullRequests++; }
    void addReview() { reviews++; }

    public int total() {
        return commits + pullRequests + reviews;
    }

    public ActivityBreakdown breakdown() {
        return new ActivityBreakdown(commits, pullRequests, reviews);
    }
}

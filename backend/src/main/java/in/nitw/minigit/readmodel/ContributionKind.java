package in.nitw.minigit.readmodel;

/**
 * The kinds of work that count as a contribution (the three spokes of the activity radar).
 *
 * <p>Each constant knows the label the SQL uses for it and how to add itself to a
 * {@link ContributionTally}, so callers never need an {@code if}/{@code switch} on strings
 * (polymorphism instead of branching).
 */
public enum ContributionKind {
    COMMIT("COMMIT") {
        @Override void addTo(ContributionTally tally) { tally.addCommit(); }
    },
    PULL_REQUEST("PR") {
        @Override void addTo(ContributionTally tally) { tally.addPullRequest(); }
    },
    REVIEW("REVIEW") {
        @Override void addTo(ContributionTally tally) { tally.addReview(); }
    };

    private final String sqlLabel;

    ContributionKind(String sqlLabel) {
        this.sqlLabel = sqlLabel;
    }

    /** Each kind records itself on the tally. */
    abstract void addTo(ContributionTally tally);

    /** Maps the label used in the activity SQL ("COMMIT", "PR", "REVIEW"). Unknown labels count as reviews, as before. */
    public static ContributionKind fromSql(String label) {
        for (ContributionKind k : values()) {
            if (k.sqlLabel.equals(label)) return k;
        }
        return REVIEW;
    }
}

package in.nitw.minigit.readmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContributionTallyTest {

    @Test
    void sqlLabelsMapToKinds() {
        assertEquals(ContributionKind.COMMIT, ContributionKind.fromSql("COMMIT"));
        assertEquals(ContributionKind.PULL_REQUEST, ContributionKind.fromSql("PR"));
        assertEquals(ContributionKind.REVIEW, ContributionKind.fromSql("REVIEW"));
    }

    @Test
    void tallyCountsEachKindSeparately() {
        ContributionTally t = new ContributionTally();
        t.record(ContributionKind.COMMIT);
        t.record(ContributionKind.COMMIT);
        t.record(ContributionKind.PULL_REQUEST);
        t.record(ContributionKind.REVIEW);
        t.record(ContributionKind.REVIEW);
        t.record(ContributionKind.REVIEW);

        assertEquals(6, t.total());
        assertEquals(2, t.breakdown().commits());
        assertEquals(1, t.breakdown().pullRequests());
        assertEquals(3, t.breakdown().reviews());
    }
}

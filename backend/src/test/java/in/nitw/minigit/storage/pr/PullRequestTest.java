package in.nitw.minigit.storage.pr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PullRequestTest {

    private PullRequest newPr() {
        return new PullRequest("Add login", "alice", "feature", "main");
    }

    @Test
    void startsOpen() {
        assertEquals(PullRequestStatus.OPEN, newPr().getStatus());
    }

    @Test
    void sameSourceAndTargetRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PullRequest("x", "alice", "main", "main"));
    }

    @Test
    void authorCannotApproveOwnPr() {
        PullRequest pr = newPr();
        assertThrows(IllegalStateException.class, () -> pr.approve("alice"));
        assertEquals(PullRequestStatus.OPEN, pr.getStatus());
    }

    @Test
    void otherUserCanApprove() {
        PullRequest pr = newPr();
        pr.approve("bob");
        assertEquals(PullRequestStatus.APPROVED, pr.getStatus());
        assertEquals("bob", pr.getApprovedBy());
    }

    @Test
    void cannotMergeBeforeApproval() {
        assertThrows(IllegalStateException.class, () -> newPr().markMerged());
    }

    @Test
    void approvedPrCanBeMerged() {
        PullRequest pr = newPr();
        pr.approve("bob");
        pr.markMerged();
        assertEquals(PullRequestStatus.MERGED, pr.getStatus());
    }

    @Test
    void closedPrCannotBeReopenedApprovedOrCommented() {
        PullRequest pr = newPr();
        pr.close();
        assertThrows(IllegalStateException.class, () -> pr.approve("bob"));
        assertThrows(IllegalStateException.class, () -> pr.comment("bob", "hi"));
        assertThrows(IllegalStateException.class, pr::close);
    }

    @Test
    void commentsAreStoredInOrder() {
        PullRequest pr = newPr();
        pr.comment("bob", "looks good");
        pr.comment("alice", "thanks");
        assertEquals(2, pr.getComments().size());
        assertEquals("bob", pr.getComments().get(0).author());
    }
}
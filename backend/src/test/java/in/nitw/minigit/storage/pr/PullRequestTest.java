package in.nitw.minigit.storage.pr;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
    void otherUserCanApproveAndStatusStaysOpen() {
        PullRequest pr = newPr();
        assertTrue(pr.approve("bob"));
        assertEquals(PullRequestStatus.OPEN, pr.getStatus());
        assertEquals(Set.of("bob"), pr.getApprovals());
        assertTrue(pr.isApproved());
    }

    @Test
    void approvingTwiceIsANoOp() {
        PullRequest pr = newPr();
        assertTrue(pr.approve("bob"));
        assertFalse(pr.approve("bob"));
        assertTrue(pr.approve("carol"));
        assertEquals(List.of("bob", "carol"), List.copyOf(pr.getApprovals()));
    }

    @Test
    void canMergeWithoutApproval() {
        PullRequest pr = newPr();
        pr.markMerged();
        assertEquals(PullRequestStatus.MERGED, pr.getStatus());
        assertFalse(pr.isApproved());
    }

    @Test
    void approvedPrCanBeMerged() {
        PullRequest pr = newPr();
        pr.approve("bob");
        pr.markMerged();
        assertEquals(PullRequestStatus.MERGED, pr.getStatus());
    }

    @Test
    void mergedPrIsFinal() {
        PullRequest pr = newPr();
        pr.markMerged();
        assertThrows(IllegalStateException.class, pr::markMerged);
        assertThrows(IllegalStateException.class, pr::close);
        assertThrows(IllegalStateException.class, () -> pr.approve("bob"));
    }

    @Test
    void statusesMatchSchemaEnum() {
        // pull_requests.status is ENUM('OPEN','MERGED','CLOSED') in V1
        assertEquals(List.of("OPEN", "MERGED", "CLOSED"),
                Arrays.stream(PullRequestStatus.values()).map(Enum::name).toList());
    }

    @Test
    void closedPrCannotBeReopenedApprovedOrCommented() {
        PullRequest pr = newPr();
        pr.close();
        assertThrows(IllegalStateException.class, () -> pr.approve("bob"));
        assertThrows(IllegalStateException.class, () -> pr.comment("bob", "hi"));
        assertThrows(IllegalStateException.class, pr::close);
        assertThrows(IllegalStateException.class, pr::markMerged);
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
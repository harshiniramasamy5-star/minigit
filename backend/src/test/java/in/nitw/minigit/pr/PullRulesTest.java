package in.nitw.minigit.pr;

import in.nitw.minigit.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PullRulesTest {

    @Test
    void newPullRequestNeedsTitleAndTwoDifferentBranches() {
        PullRules.checkNew("Add login", null, "feat/login", "main");
        PullRules.checkNew("Add login", "why", "feat/login", "main");
        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class, () -> PullRules.checkNew(" ", null, "a", "b")).getCode());
        assertThrows(ApiException.class, () -> PullRules.checkNew(null, null, "a", "b"));
        assertThrows(ApiException.class, () -> PullRules.checkNew("t", null, "a", "a"));
        assertThrows(ApiException.class, () -> PullRules.checkNew("t", null, "", "b"));
        assertThrows(ApiException.class, () -> PullRules.checkNew("t", null, "a", null));
        assertThrows(ApiException.class, () -> PullRules.checkNew("x".repeat(201), null, "a", "b"));
        assertThrows(ApiException.class, () -> PullRules.checkNew("t", "x".repeat(5001), "a", "b"));
    }

    @Test
    void onlyOpenPullRequestsCanChange() {
        PullRules.requireOpen("OPEN");
        for (String done : new String[]{"MERGED", "CLOSED"}) {
            ApiException e = assertThrows(ApiException.class, () -> PullRules.requireOpen(done));
            assertEquals(409, e.getStatus().value());
            assertEquals("PR_NOT_OPEN", e.getCode());
        }
    }

    @Test
    void authorsCannotApproveTheirOwnPullRequest() {
        PullRules.checkApprover("asha", "arisha");
        ApiException e = assertThrows(ApiException.class, () -> PullRules.checkApprover("asha", "asha"));
        assertEquals(403, e.getStatus().value());
    }

    @Test
    void statusFilterDefaultsToOpenAndAcceptsAll() {
        assertEquals("OPEN", PullRules.statusFilter(null));
        assertEquals("OPEN", PullRules.statusFilter(" "));
        assertEquals("MERGED", PullRules.statusFilter("merged"));
        assertEquals("CLOSED", PullRules.statusFilter("CLOSED"));
        assertNull(PullRules.statusFilter("all"));
        assertThrows(ApiException.class, () -> PullRules.statusFilter("APPROVED"));
    }

    @Test
    void commentsMustHaveTextAndStayShort() {
        assertEquals("looks good", PullRules.cleanComment("  looks good \n"));
        assertThrows(ApiException.class, () -> PullRules.cleanComment(""));
        assertThrows(ApiException.class, () -> PullRules.cleanComment("   "));
        assertThrows(ApiException.class, () -> PullRules.cleanComment(null));
        assertThrows(ApiException.class, () -> PullRules.cleanComment("x".repeat(5001)));
    }
}

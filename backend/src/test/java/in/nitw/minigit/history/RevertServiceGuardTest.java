package in.nitw.minigit.history;

import in.nitw.minigit.branch.BranchProtection;
import in.nitw.minigit.branch.BranchStore;
import in.nitw.minigit.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The refusals that happen before any commit is read: no database or file store needed. */
class RevertServiceGuardTest {

    private static final String HASH = "a".repeat(64);

    private static RevertService service(boolean branchExists, boolean protectedBranch) {
        BranchStore branches = new BranchStore(null) {
            @Override
            public Optional<Branch> find(long repoId, String name) {
                return branchExists ? Optional.of(new Branch(1, repoId, name, HASH, false)) : Optional.empty();
            }
        };
        BranchProtection protection = new BranchProtection(null) {
            @Override
            public boolean isRequired(long repoId, String name) {
                return protectedBranch;
            }
        };
        return new RevertService(null, branches, null, null, null, protection);
    }

    private static ApiException revert(RevertService s, String branch, String hash) {
        return assertThrows(ApiException.class, () -> s.revert(1, 1, "user", "main", branch, hash));
    }

    @Test
    void protectedBranchIsRefusedWith403() {
        ApiException e = revert(service(true, true), "main", HASH);
        assertEquals(403, e.getStatus().value());
        assertEquals("BRANCH_PROTECTED", e.getCode());
    }

    @Test
    void unknownBranchIs404() {
        assertEquals(404, revert(service(false, false), "nope", HASH).getStatus().value());
    }

    @Test
    void malformedCommitHashIs404() {
        assertEquals(404, revert(service(true, false), "main", "xyz").getStatus().value());
    }

    @Test
    void blankBranchIs400() {
        assertEquals(400, revert(service(true, false), " ", HASH).getStatus().value());
    }
}

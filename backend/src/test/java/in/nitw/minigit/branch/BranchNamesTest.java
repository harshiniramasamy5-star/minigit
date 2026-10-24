package in.nitw.minigit.branch;

import in.nitw.minigit.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchNamesTest {

    @Test void validNames() {
        for (String n : new String[]{"main", "feat/login", "fix-12", "v1.2", "a_b"}) {
            assertEquals(n, BranchNames.check(n));
        }
    }

    @Test void invalidNames() {
        for (String n : new String[]{null, "", "/x", "x/", "a//b", "a..b", ".hidden", "x.lock", "-f", "has space", "a\\b"}) {
            assertThrows(ApiException.class, () -> BranchNames.check(n), String.valueOf(n));
        }
    }
}

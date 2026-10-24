package in.nitw.minigit.repo;

import in.nitw.minigit.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepoNamesTest {

    @Test
    void acceptsNormalNames() {
        for (String ok : new String[]{"minigit", "my-repo_2.0", "A", "a".repeat(100), ".github"}) {
            assertEquals(ok, RepoNames.check(ok));
        }
    }

    @Test
    void rejectsPathTricksAndOddCharacters() {
        for (String bad : new String[]{null, "", " ", "a b", "a/b", "../x", "..", ".", "a\\b", "x".repeat(101),
                "naïve", "name;drop", "foo.git"}) {
            ApiException e = assertThrows(ApiException.class, () -> RepoNames.check(bad), String.valueOf(bad));
            assertEquals(400, e.getStatus().value());
        }
    }

    @Test
    void reservedNamesAreRefusedBecauseTheyClashWithRoutes() {
        for (String reserved : new String[]{"branches", "Merges", "mine", "collaborators"}) {
            assertThrows(ApiException.class, () -> RepoNames.check(reserved));
        }
    }

    @Test
    void collaboratorRolesAreReadOrWriteOnly() {
        assertEquals("READ", CollaboratorController.parseRole(" read "));
        assertEquals("WRITE", CollaboratorController.parseRole("WRITE"));
        for (String bad : new String[]{null, "", "OWNER", "ADMIN", "owner"}) {
            assertThrows(ApiException.class, () -> CollaboratorController.parseRole(bad));
        }
    }
}

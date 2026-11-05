package in.nitw.minigit.cli;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The messages people see for each backend error (no server needed). */
class ApiClientErrorTest {

    private static CliException map(int status, String json) {
        return ApiClient.mapError(status, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void expiredTokenTellsThemToLoginAgain() {
        CliException e = map(401, "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Please log in\"}");
        assertTrue(e.getMessage().contains("expired") && e.getMessage().contains("minigit login"));
        assertEquals(2, e.exitCode());
    }

    @Test
    void wrongPasswordIsNotCalledExpired() {
        assertEquals("Wrong username or password.",
                map(401, "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Wrong username or password\"}").getMessage());
    }

    @Test
    void movedBranchSaysPullFirst() {
        String m = map(409, "{\"code\":\"BRANCH_HEAD_MOVED\",\"message\":\"x\"}").getMessage();
        assertTrue(m.contains("branch moved") && m.contains("minigit pull"));
    }

    @Test
    void other409KeepsTheServerMessage() {
        assertEquals("Branch already exists (NAME_TAKEN)",
                map(409, "{\"code\":\"NAME_TAKEN\",\"message\":\"Branch already exists\"}").getMessage());
    }

    @Test
    void lockedAccountShowsTheMinutes() {
        String m = map(423, "{\"code\":\"ACCOUNT_LOCKED\",\"message\":\"Try again in 14 minutes.\"}").getMessage();
        assertTrue(m.contains("5 failed logins") && m.contains("14 minutes"));
    }

    @Test
    void serverErrorsHideTheDetails() {
        assertTrue(map(500, "{\"code\":\"INTERNAL_ERROR\",\"message\":\"stack...\"}").getMessage().contains("internal error"));
        assertTrue(map(502, "<html>bad gateway</html>").getMessage().contains("internal error"));
    }
}

package in.nitw.minigit.cli;

import in.nitw.minigit.cli.commands.Clone;
import in.nitw.minigit.cli.commands.Login;
import in.nitw.minigit.cli.commands.Logout;
import in.nitw.minigit.cli.commands.Pull;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real commands against a RUNNING backend. Skipped unless MINIGIT_IT_SERVER is set:
 *
 *   MINIGIT_IT_SERVER=http://localhost:8080 mvn -f cli/pom.xml test -Dtest=CliIntegrationTest
 *
 * It registers throw-away users, so run it against a dev database. Credentials go to a temp "home", not yours.
 */
class CliIntegrationTest {

    private static final String PW = "pw12345678";
    private static final PrintStream QUIET = new PrintStream(OutputStream.nullOutputStream());

    private static void stdin(String line) {
        System.setIn(new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private static String enc(String s) {
        return ApiClient.seg(s);
    }

    /** One commit through the real HTTP API (upload files to the working copy, then commit). */
    private static void commitViaApi(ApiClient api, String owner, String repo, String message,
                                     Map<String, String> files, String... deletes) {
        String base = "/repos/" + enc(owner) + "/" + enc(repo);
        api.get(base + "/worktree", null);   // first call checks out the default branch
        for (var f : files.entrySet()) {
            api.putMultipart(base + "/worktree/files", Map.of("path", f.getKey()), "file", f.getKey(),
                    f.getValue().getBytes(StandardCharsets.UTF_8));
        }
        for (String d : deletes) api.delete(base + "/worktree/files", Map.of("path", d));
        api.post(base + "/commits", Map.of("message", message));
    }

    @Test
    void loginCloneAndPullAgainstTheRealBackend() throws Exception {
        String server = System.getenv("MINIGIT_IT_SERVER");
        Assumptions.assumeTrue(server != null && !server.isBlank(), "MINIGIT_IT_SERVER not set");

        Path tmp = Files.createTempDirectory("minigit-cli-it");
        System.setProperty("user.home", tmp.resolve("home").toString());
        String id = UUID.randomUUID().toString().substring(0, 8);
        String user = "it" + id, repo = "cli-" + id;
        ApiClient.anonymous(server).post("/auth/register",
                Map.of("username", user, "email", user + "@example.com", "password", PW));

        // login: wrong password, then right password
        stdin("not-the-password");
        CliException bad = assertThrows(CliException.class, () -> new Login(QUIET).run("--server", server, user));
        assertEquals("Wrong username or password.", bad.getMessage());
        stdin(PW);
        assertEquals(0, new Login(QUIET).run("--server", server, user));
        assertTrue(Credentials.load().orElseThrow().expiresAt().isAfter(java.time.Instant.now()));

        // a repo with 2 files, one nested
        ApiClient api = ApiClient.fromCredentials();
        api.post("/repos", Map.of("name", repo, "visibility", "PRIVATE"));
        commitViaApi(api, user, repo, "first", Map.of("README.md", "hello\n", "src/App.txt", "v1\n"));

        // clone
        Path dir = tmp.resolve("work").resolve(repo);
        assertEquals(0, new Clone(QUIET).run(user + "/" + repo, dir.toString()));
        assertEquals("hello\n", Files.readString(dir.resolve("README.md")));
        assertEquals("v1\n", Files.readString(dir.resolve("src/App.txt")));
        assertEquals("main", RepoConfig.find(dir).branch);

        // someone commits again; pull brings it in
        commitViaApi(api, user, repo, "second", Map.of("README.md", "hello v2\n", "notes.txt", "n\n"), "src/App.txt");
        assertEquals(0, new Pull(QUIET, dir).run());
        assertEquals("hello v2\n", Files.readString(dir.resolve("README.md")));
        assertEquals("n\n", Files.readString(dir.resolve("notes.txt")));
        assertFalse(Files.exists(dir.resolve("src/App.txt")));

        // a local edit is protected from the next pull
        Files.writeString(dir.resolve("README.md"), "my edit\n");
        commitViaApi(api, user, repo, "third", Map.of("README.md", "hello v3\n"));
        assertEquals(1, new Pull(QUIET, dir).run());
        assertEquals("my edit\n", Files.readString(dir.resolve("README.md")));

        // logout, then commands ask for a login
        assertEquals(0, new Logout(QUIET).run());
        CliException out = assertThrows(CliException.class, () -> new Pull(QUIET, dir).run());
        assertTrue(out.getMessage().contains("not logged in"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        String server = System.getenv("MINIGIT_IT_SERVER");
        Assumptions.assumeTrue(server != null && !server.isBlank(), "MINIGIT_IT_SERVER not set");

        System.setProperty("user.home", Files.createTempDirectory("minigit-cli-lock").toString());
        String user = "lock" + UUID.randomUUID().toString().substring(0, 8);
        ApiClient.anonymous(server).post("/auth/register",
                Map.of("username", user, "email", user + "@example.com", "password", PW));
        CliException last = null;
        for (int i = 0; i < 5; i++) {
            stdin("wrong-password");
            last = assertThrows(CliException.class, () -> new Login(QUIET).run("--server", server, user));
        }
        assertTrue(last.getMessage().contains("Account locked after 5 failed logins"), last.getMessage());
    }
}

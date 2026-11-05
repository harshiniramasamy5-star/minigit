package in.nitw.minigit.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import in.nitw.minigit.cli.ApiClient;
import in.nitw.minigit.cli.CliException;
import in.nitw.minigit.cli.Credentials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * minigit login [--server URL] [username-or-email]
 * Password comes from $MINIGIT_PASSWORD (scripts and tests), else a hidden prompt, else a line from stdin.
 * The server defaults to $MINIGIT_SERVER, else http://localhost:8080. The token lives 30 minutes, then login again.
 */
public final class Login {

    private final PrintStream out;

    public Login() {
        this(System.out);
    }

    public Login(PrintStream out) {
        this.out = out;
    }

    public int run(String... args) {
        String server = System.getenv().getOrDefault("MINIGIT_SERVER", "http://localhost:8080");
        String user = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--server")) {
                if (i + 1 >= args.length) throw new CliException("--server needs a URL, e.g. --server http://localhost:8080");
                server = args[++i];
            } else if (user == null && !args[i].startsWith("--")) {
                user = args[i];
            } else {
                throw new CliException("Usage: minigit login [--server URL] [username-or-email]");
            }
        }
        if (user == null) user = prompt("Username or email: ");
        String password = System.getenv("MINIGIT_PASSWORD");
        if (password == null) password = promptSecret("Password: ");
        if (user.isBlank() || password.isEmpty()) throw new CliException("Enter a username and a password.");

        JsonNode res = ApiClient.anonymous(server)
                .post("/auth/login", Map.of("usernameOrEmail", user.strip(), "password", password));
        String token = res.path("token").asText(null);
        if (token == null) throw new CliException("The server did not return a token.");
        Instant expires = ApiClient.instant(res.get("expiresAt"));
        String name = res.path("user").path("username").asText(user);
        new Credentials(server.replaceAll("/+$", ""), token, expires, name).save();

        out.println("Logged in as " + name + " on " + server + ".");
        if (expires != null) {
            out.println("This login is valid until "
                    + DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(expires)
                    + " (30 minutes). After that, run `minigit login` again.");
        }
        return 0;
    }

    private String prompt(String label) {
        out.print(label);
        out.flush();
        return readLine();
    }

    private String promptSecret(String label) {
        if (System.console() != null) {
            char[] pw = System.console().readPassword(label);
            return pw == null ? "" : new String(pw);
        }
        return prompt(label);
    }

    private static String readLine() {
        try {
            String line = new BufferedReader(new InputStreamReader(System.in)).readLine();
            return line == null ? "" : line;
        } catch (IOException e) {
            throw new CliException("Can't read input.");
        }
    }
}

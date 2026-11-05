package in.nitw.minigit.cli.commands;

import in.nitw.minigit.cli.Credentials;

import java.io.PrintStream;

/** minigit logout: deletes ~/.minigit/credentials. There is no logout endpoint; dropping the token is the logout. */
public final class Logout {

    private final PrintStream out;

    public Logout() {
        this(System.out);
    }

    public Logout(PrintStream out) {
        this.out = out;
    }

    public int run(String... args) {
        out.println(Credentials.delete() ? "Logged out." : "You were not logged in.");
        return 0;
    }
}

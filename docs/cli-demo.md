# CLI demo script (about 3 minutes)

Backend running (`MINIGIT_OPEN_DEV=true mvn spring-boot:run`) and a repo with a few commits. Use the real user names below.

1. **Wrong password:** `minigit login --server http://localhost:8080 harshini`, type a wrong password.
   Shows: "Wrong username or password."
2. **Login:** same command, right password. Shows the user and the time the login expires (30 minutes).
3. **Clone:** `minigit clone harshini/minigit-demo` then `cd minigit-demo && ls -R`. Files appear; `cat .minigit/config` shows the branch and base commit.
4. **Change on the web:** in the browser, commit a change to the same repo (edit a file, add one, delete one).
5. **Pull:** `minigit pull`. Shows "N updated, M removed". `git`-style: only changed files are fetched.
6. **Local edit is protected:** edit a file locally, change the same file on the web, `minigit pull`.
   Shows the file under "left alone" and exits 1. Nothing is overwritten.
7. **Expiry / logout:** `minigit logout`, then `minigit pull` -> "You are not logged in. Run `minigit login` first."
   (If 30 minutes have passed: "Your login has expired ... run `minigit login` again.")
8. **Lock (optional, use a throw-away user):** 5 wrong passwords -> "Account locked after 5 failed logins. Try again in 15 minutes."

Integration test (backend running): `MINIGIT_IT_SERVER=http://localhost:8080 mvn -f cli/pom.xml test -Dtest=CliIntegrationTest`

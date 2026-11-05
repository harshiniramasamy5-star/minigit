# MiniGit CLI

Build: `cd cli && mvn package` (jar: `cli/target/minigit.jar`). Run from the repo root: `./minigit <command>`.

| Command | What it does |
|---|---|
| `login [--server URL] [user]` | Saves a 30-minute token in `~/.minigit/credentials` |
| `logout` | Deletes it |
| `clone owner/repo [dir] [-b branch]` | Downloads the branch; writes `.minigit/config` and `.minigit/index` |
| `status` | Added / modified / deleted files vs `.minigit/index` (local only) |
| `commit -m "msg"` | Saves the message in `.minigit/pending` (local only) |
| `push [--new-branch name]` | Uploads changed files to the server working copy and creates ONE commit |
| `pull` | Updates files from the server; never overwrites local edits |

Push flow: branch head must equal `base` in `.minigit/config` (else "branch moved", use `--new-branch`);
then `worktree/checkout`, `worktree/discard`, `PUT`/`DELETE worktree/files`, `POST commits`.
On success `.minigit/index` and `base` are updated and the pending messages are cleared.

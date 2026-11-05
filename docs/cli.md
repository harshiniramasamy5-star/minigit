# MiniGit CLI: shared pieces (ApiClient, ReadApi, RepoConfig)

Package `in.nitw.minigit.cli` (change the `package` lines if the module uses another name).
Needs Java 17 and `jackson-databind` only (no jsr310 module).

## ApiClient: use this for every request
```java
ApiClient api = ApiClient.fromCredentials();          // reads ~/.minigit/credentials; throws CliException if not logged in / expired
JsonNode me  = api.get("/auth/me", null);
JsonNode out = api.post("/repos/" + ApiClient.seg(owner) + "/" + ApiClient.seg(repo) + "/commits", Map.of("message", "msg"));
byte[] raw   = api.getBytes(path, Map.of("ref", "main", "path", "src/A.java"));
api.delete(path, Map.of("path", "old.txt"));
api.putMultipart("/repos/o/r/worktree/files", Map.of("path", "src/A.java"), "file", "A.java", bytes);   // push uses this
```
Paths are relative to `/api/v1`. Build path segments with `ApiClient.seg(...)`. Every failure is a `CliException`
(print `getMessage()`, exit with `exitCode()`; exit code 2 = log in again):

| Server says | Message |
|---|---|
| 401 (not login) / token past `expiresAt` | Your login expired ... Run `minigit login` again. (checked locally first, no request sent) |
| 401 INVALID_CREDENTIALS | Wrong username or password. |
| 423 | Account locked after 5 failed logins. + the server's "try again in N minutes" |
| 409 BRANCH_HEAD_MOVED | The branch moved on the server ... Run `minigit pull`, then try again. |
| other 409 / 403 / 404 / 400 | the server's message |
| 5xx | internal error, try again |
| no connection | Can't reach the server at ... Is the backend running? |

## Files in a cloned folder
- `.minigit/config`: `server`, `owner`, `repo`, `branch`, `base` (the commit last synced; `RepoConfig.find(cwd)` walks up to find it)
- `.minigit/index`: `sha256<TAB>path` per tracked file = what clone/pull last wrote. `status` can compare the current file hash
  to this to find local changes; `RepoConfig.sha256(bytes)`, `readIndex()`, `writeIndex(map)` are there for that.
- `RepoConfig.resolveSafe(repoPath)` turns a server path into a file under the folder and refuses `..`, absolute paths and `.minigit/`. Use it for every path that comes from the server.
After a successful push/commit, update `cfg.base` and the index (and `cfg.save()`), or the next pull will re-fetch your own change.

## Commands in this push
`Login`, `Logout`, `Clone`, `Pull` are plain classes with `int run(String... args)` (return = exit code, throw `CliException` for errors),
so they plug into picocli or a hand-written dispatcher.

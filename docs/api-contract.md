# MiniGit API contract (draft v0.3, for team review)

Base path: `/api/v1`. JSON everywhere except file upload (`multipart/form-data`) and raw file download.
Auth: `Authorization: Bearer <jwt>` (30-min fixed expiry, no refresh). Hashes are 64-char SHA-256 hex.
Repos are addressed as `{owner}/{repo}` (username + repo name), like GitHub.

Status for review: **nothing here is final until all four of us OK it.** Change it by PR to this file.

**v0.3 changes (U1/U2/U9/U11 integrated, 2026-10-24):** `INVALID_CREDENTIALS` and `REPO_IN_RECYCLE_BIN` error codes,
login/lockout rules spelled out, reserved repo names, recycle-bin permissions, branch delete is now a soft delete (U11).
`GET /repos/{owner}/{repo}/collaborators` needs a role on the repo.

**v0.2 changes (Harshini, 2026-10-24):** PR merge no longer returns conflicts as a 409 (`ApiError` has no
`sessionId` field), approvals are recorded only, `INVALID_PATH` and `INTERNAL_ERROR` rows added, commit time rule
added. Two new questions at the bottom.

## Errors (same shape for every endpoint)

```json
{ "status": 409, "code": "BRANCH_HEAD_MOVED", "message": "The branch moved since you started...",
  "fieldErrors": [], "timestamp": "2026-10-20T10:00:00Z" }
```

| HTTP | code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation failed; see `fieldErrors` |
| 400 | `INVALID_PATH` | `..`, absolute path, backslash, `.minigit/`, two paths that normalise to the same file, file/folder clash (`a` and `a/b`). `a//b` and `./a` are accepted and normalised. |
| 401 | `UNAUTHENTICATED` | Missing/expired token |
| 401 | `INVALID_CREDENTIALS` | Login with an unknown user or wrong password (same message for both) |
| 403 | `FORBIDDEN` | Role too low (Table 2) |
| 404 | `NOT_FOUND` | Also used for private repos the caller can't see (don't leak existence) |
| 409 | `BRANCH_HEAD_MOVED` | Commit on a branch whose head moved since checkout |
| 409 | `MERGE_IN_PROGRESS` | Action blocked by an open merge session |
| 409 | `NAME_TAKEN` | Username, email, repo or branch name already exists. A repo or branch name that is in the recycle bin counts as taken (its message says so). |
| 409 | `REPO_IN_RECYCLE_BIN` | Restoring a branch whose repository is still in the bin |
| 423 | `ACCOUNT_LOCKED` | 5 failed logins, locked 15 min |
| 500 | `INTERNAL_ERROR` | Anything unhandled (logged on the server) |

The error body never carries extra data (no `sessionId`, no `commit`). If the client needs data back, it's a 2xx.

Paged lists: `?page=0&size=20` → `{ "items": [...], "page": 0, "size": 20, "total": 57 }`.

## U1 Auth: Shrejal

| Method | Path | Body / notes | Response |
|---|---|---|---|
| POST | `/auth/register` | `{username, email, password}` | 201 `{id, username, email}` |
| POST | `/auth/login` | `{usernameOrEmail, password}` | 200 `{token, expiresAt, user}` |
| GET | `/auth/me` | | `{id, username, email}` |

No logout endpoint: client drops the token.

Rules: username 3-40 of `A-Za-z0-9_-`; password 8-72 characters; email stored lowercase. The token's expiry is **fixed** at
30 minutes from login (no refresh, no sliding window), `expiresAt` in the login response says when. 5 wrong passwords lock the
account for 15 minutes (423 `ACCOUNT_LOCKED`, even the right password is refused meanwhile); success resets the count.

## U2 Repositories: Shrejal

| Method | Path | Body / notes | Response |
|---|---|---|---|
| POST | `/repos` | `{name, description?, visibility}`; creates `main` + empty initial commit | 201 Repo |
| GET | `/repos/mine` | repos I own or collaborate on | paged Repo |
| GET | `/repos/{owner}/{repo}` | | Repo |
| PATCH | `/repos/{owner}/{repo}` | `{name?, description?, visibility?}` (Owner) | Repo |
| DELETE | `/repos/{owner}/{repo}` | soft delete → recycle bin (Owner) | 204 |

Repo = `{id, owner, name, description, visibility, defaultBranch, myRole, createdAt, updatedAt}`
(`myRole` is `OWNER`, `WRITE`, `READ` or `null` for guests / non-collaborators of a public repo; `id` is what the
`/repos/{repoId}/...` branch and merge endpoints take.)

Names: `A-Za-z0-9._-`, 1-100 chars, not `.`/`..`, not ending in `.git`, and not `branches`, `merges`, `mine`, `collaborators`
(they would collide with other routes). A private repo the caller can't see is a 404, never a 403. Delete and rename are Owner only.

## U9 Collaborators: Shrejal

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/collaborators` | any role on the repo; owner first (`role: OWNER`) | `[{userId, username, role}]` |
| PUT | `/repos/{owner}/{repo}/collaborators/{username}` | `{role: READ\|WRITE}` (Owner); adds or changes; the owner's own row can't be changed | `{userId, username, role}` |
| DELETE | `/repos/{owner}/{repo}/collaborators/{username}` | (Owner) | 204 |

## U11 Recycle bin: Shrejal

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/recycle-bin` | items I deleted, plus anything deleted from repos I own; `repo` = `owner/name`; expired rows hidden | `[{id, itemType: REPOSITORY\|BRANCH, name, repo, deletedAt, expiresAt}]` |
| POST | `/recycle-bin/{id}/restore` | repo: owner only; branch: whoever deleted it or the repo owner; 409 `REPO_IN_RECYCLE_BIN` | 204 |
| DELETE | `/recycle-bin/{id}` | permanent delete now (same permissions); a repo's folder in the file store goes too | 204 |

The `id` is the recycle-bin row id. Items are removed automatically 7 days after deletion (daily job at 03:00).

## U3 Working copy + commit: Arisha

| Method | Path | Body / notes | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/worktree` | my working copy state | `{branch, baseCommit, changes: [{path, status: ADDED\|MODIFIED\|DELETED}]}` |
| POST | `/repos/{owner}/{repo}/worktree/checkout` | `{branch}` | worktree state |
| PUT | `/repos/{owner}/{repo}/worktree/files` | multipart `path`, `file` (Write) | 204 |
| DELETE | `/repos/{owner}/{repo}/worktree/files?path=` | (Write) | 204 |
| POST | `/repos/{owner}/{repo}/worktree/discard` | reset to branch head | 204 |
| POST | `/repos/{owner}/{repo}/commits` | `{message, newBranch?}`; 409 `BRANCH_HEAD_MOVED` unless `newBranch` given | 201 Commit |

## U4 History, diff, restore: Arisha

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/commits?branch=` | paged, newest first | paged Commit |
| GET | `/repos/{owner}/{repo}/commits/{hash}` | | Commit + `files: [{path, status}]` |
| GET | `/repos/{owner}/{repo}/tree?ref=&path=` | `ref` = branch or hash | `[{name, type: FILE\|DIR, blobHash?}]` |
| GET | `/repos/{owner}/{repo}/blob?ref=&path=` | raw bytes, `text/plain` or `application/octet-stream` | file |
| GET | `/repos/{owner}/{repo}/diff?from=&to=` | Myers diff | `[{path, status, hunks: [{oldStart, oldLines, newStart, newLines, lines: [{type: CONTEXT\|ADD\|DEL, text}]}]}]` |
| POST | `/repos/{owner}/{repo}/restore` | `{branch, commit, path?}`; whole commit or one file (Write) | 201 Commit |
| GET | `/repos/{owner}/{repo}/graph?limit=200` | for the commit graph (Harshini's UI) | `{commits: [{hash, parents, message, author, createdAt}], branches: [{name, head}]}` |

Commit = `{hash, treeHash, parents: [hash], author, message, createdAt}`

`createdAt` is the time stored inside the commit object. Services write commits with
`RepoObjectStores.writeCommit(...)` and save `StoredCommit.createdAt()` to `commits.created_at`, so the two always
match (millisecond precision). Don't rely on the column default. File paths go through
`RepoObjectStores.buildTree(...)`, which runs `PathValidator` first.

## U5 Branches: Aakriti

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/branches` | | `[{name, head, isProtected, isDefault}]` |
| POST | `/repos/{owner}/{repo}/branches` | `{name, fromRef}` (Write) | 201 Branch |
| DELETE | `/repos/{owner}/{repo}/branches/{name}` | → recycle bin; `main` blocked (Write) | 204 |

Branch names with `/` (e.g. `feat/login`) are URL-encoded in the path.

## U6 / U7 Merge + conflicts: Aakriti

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/repos/{owner}/{repo}/merges` | `{source, target}` (Write) | `{result: UP_TO_DATE\|FAST_FORWARD\|MERGED\|CONFLICTS, commit?, sessionId?}` |
| GET | `/repos/{owner}/{repo}/merges/{sessionId}` | | `{base, ours, theirs, conflicts: [{id, path, type, resolution}]}` |
| GET | `/repos/{owner}/{repo}/merges/{sessionId}/conflicts/{id}` | | `{path, base?, ours?, theirs?}` (text) |
| PUT | `/repos/{owner}/{repo}/merges/{sessionId}/conflicts/{id}` | `{resolution: OURS\|THEIRS\|MANUAL, content?}` | conflict |
| POST | `/repos/{owner}/{repo}/merges/{sessionId}/complete` | `{message}`; all conflicts resolved | 201 Commit |
| POST | `/repos/{owner}/{repo}/merges/{sessionId}/abort` | | 204 |

## U10 Undo: Aakriti

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/branches/{name}/undo` | peek top of stack | `{actionType, headBefore, headAfter, createdAt}` or 204 |
| POST | `/repos/{owner}/{repo}/branches/{name}/undo` | (Write) | `{newHead}` |

## U8 Pull requests: Arisha

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/pulls?status=OPEN` | | paged PR |
| POST | `/repos/{owner}/{repo}/pulls` | `{title, description?, source, target}` (Write) | 201 PR |
| GET | `/repos/{owner}/{repo}/pulls/{number}` | | PR + `diff`, `comments`, `approvals` |
| POST | `/repos/{owner}/{repo}/pulls/{number}/comments` | `{body}` (Read) | 201 comment |
| POST | `/repos/{owner}/{repo}/pulls/{number}/approve` | not the author, PR must be OPEN; approving twice is a no-op (Write) | 204 |
| POST | `/repos/{owner}/{repo}/pulls/{number}/merge` | runs U6, approval **not** required (Write) | `{result: UP_TO_DATE\|FAST_FORWARD\|MERGED\|CONFLICTS, commit?, sessionId?, pr}` |
| POST | `/repos/{owner}/{repo}/pulls/{number}/close` | | PR |

PR = `{number, title, description, author, source, target, status: OPEN|MERGED|CLOSED, approvals: [username], createdAt, closedAt?}`

There is no `APPROVED` status. Approving adds a row to `pr_approvals` and the PR stays `OPEN` (SRS v1.2: approval
recorded, not required to merge). On `CONFLICTS` the PR stays `OPEN`; the user resolves through
`/merges/{sessionId}` and the PR becomes `MERGED` when that session completes.

## Harshini: explore, dashboard, health

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/explore?q=&page=` | public repos only, matches name/description | paged Repo |
| GET | `/dashboard` | my repos, open PRs on my repos, recent commits | `{repos, openPulls, recentCommits}` |
| GET | `/health` | stack smoke test; `devAuth` is true while open-dev mode is on | `{status, db, devAuth}` |

## Questions to settle in the meeting
1. Do PR merges reuse `/merges` (one code path), or does U8 call the merge service directly? (Proposed: call the service directly, same result shape.)
2. Restore = new commit on the branch (proposed) or move the head back? Undo already covers "move back".
3. Max upload size, max files per commit, max repo size: depends on V-01..V-09.
4. Who owns U10 (undo)? This file says Aakriti; the SRS v1.2 review assigned U10 and U11 to Shrejal (still to confirm).
5. When a PR merge hits conflicts and the session is completed through `/merges/{sessionId}/complete`, which code
   marks the PR `MERGED`: the U6 service (needs to know the PR) or a U8 hook? Proposed: `merge_sessions` gets an
   optional `pr_id` in a V2 migration.

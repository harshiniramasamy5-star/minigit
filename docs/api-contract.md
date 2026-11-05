# MiniGit API contract (draft v0.5, for team review)

Base path: `/api/v1`. JSON everywhere except file upload (`multipart/form-data`) and raw file download.
Auth: `Authorization: Bearer <jwt>` (30-min fixed expiry, no refresh). Hashes are 64-char SHA-256 hex.
Repos are addressed as `{owner}/{repo}` (username + repo name), like GitHub.

Status for review: **nothing here is final until all four of us OK it.** Change it by PR to this file.

**v0.6 changes (Harshini, 2026-11-05):** branch protection (`require_approval`), blame, revert; new codes
`BRANCH_PROTECTED`, `APPROVAL_REQUIRED`, `REVERT_CONFLICT`, `CANNOT_REVERT`, `NOT_IN_BRANCH`, `NOTHING_TO_REVERT`, `BLAME_BINARY`,
`BLAME_TOO_LARGE`; approval is optional by default and required on protected branches; an approval counts for the source
branch head it was given on.

**v0.5 changes (U8 built, 2026-10-24):** pull request shapes as built (`{pr, diff, comments}` detail), `PR_NOT_OPEN` /
`PR_EXISTS` / `NOTHING_TO_MERGE` codes, list defaults to `OPEN` (`status=ALL` for everything), close allowed for the author,
question 5 settled (a merge event marks the PR MERGED).

**v0.4 changes (U3/U4 built, 2026-10-24):** worktree state carries `files`/`headMoved`, new `GET /worktree/file`,
`WORKTREE_DIRTY` / `NOTHING_TO_COMMIT` / `NOTHING_TO_RESTORE` codes, `/tree` and `/diff` response shapes as built,
`GET /branches` for readers, restore = new commit (question 2 settled).

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
| 400 | `NOTHING_TO_MERGE` | Opening a pull request whose source has nothing the target doesn't already have |
| 409 | `PR_NOT_OPEN` / `PR_EXISTS` | Acting on a merged or closed pull request / an open pull request for that branch pair already exists |
| 400 | `NOTHING_TO_COMMIT` / `NOTHING_TO_RESTORE` | The working copy equals its base commit / the branch already matches that version |
| 409 | `MERGE_IN_PROGRESS` | Action blocked by an open merge session |
| 409 | `WORKTREE_DIRTY` | Switching branch, or restoring onto the branch you are on, while you have uncommitted changes |
| 409 | `NAME_TAKEN` | Username, email, repo or branch name already exists. A repo or branch name that is in the recycle bin counts as taken (its message says so). |
| 409 | `REPO_IN_RECYCLE_BIN` | Restoring a branch whose repository is still in the bin |
| 403 | `BRANCH_PROTECTED` | Commit, direct merge, restore, undo or revert on a branch that has the approval rule on |
| 409 | `APPROVAL_REQUIRED` | Merging a pull request (or finishing its merge session) into a protected branch without an approval from someone other than the author |
| 409 | `REVERT_CONFLICT` | Revert would conflict with later changes; nothing is changed |
| 400 | `CANNOT_REVERT` / `NOT_IN_BRANCH` / `NOTHING_TO_REVERT` | First or merge commit; commit not in that branch's history; its changes are already gone |
| 400 | `BLAME_BINARY` / `BLAME_TOO_LARGE` | Blame on a binary file, a file over 2000 lines or 512 KB, or a history needing over 500 steps |
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
| GET | `/repos/{owner}/{repo}/worktree` | my working copy state (first call checks out the default branch); Write | `{branch, baseCommit, headCommit, headMoved, changes: [{path, status: ADDED\|MODIFIED\|DELETED}], files: [{path, size}]}` |
| POST | `/repos/{owner}/{repo}/worktree/checkout` | `{branch}`; 409 `WORKTREE_DIRTY` if uncommitted changes | worktree state |
| GET | `/repos/{owner}/{repo}/worktree/file?path=` | current content of one working-copy file (Write) | raw bytes |
| PUT | `/repos/{owner}/{repo}/worktree/files` | multipart `path`, `file` (Write) | 204 |
| DELETE | `/repos/{owner}/{repo}/worktree/files?path=` | (Write) | 204 |
| POST | `/repos/{owner}/{repo}/worktree/discard` | throw away changes, move to the branch's current head (how you recover from `BRANCH_HEAD_MOVED`) | 204 |
| POST | `/repos/{owner}/{repo}/commits` | `{message, newBranch?}`; 409 `BRANCH_HEAD_MOVED` unless `newBranch` given | 201 Commit |

## U4 History, diff, restore: Arisha

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/commits?branch=` | paged, newest first | paged Commit |
| GET | `/repos/{owner}/{repo}/commits/{hash}` | | Commit + `files: [{path, status}]` |
| GET | `/repos/{owner}/{repo}/tree?ref=&path=` | `ref` = branch or hash (default: default branch), `path` = folder; folders first | `[{name, type: FILE\|DIR, size}]` |
| GET | `/repos/{owner}/{repo}/blob?ref=&path=` | raw bytes, `text/plain` or `application/octet-stream` | file |
| GET | `/repos/{owner}/{repo}/diff?from=&to=` | Myers diff; `from` may be blank (compare with an empty project); refs are branch or hash | `[{path, status: ADDED\|DELETED\|MODIFIED, binary, lines: [{type: EQUAL\|INSERT\|DELETE, text}]}]` (binary or >1 MB files have `binary: true` and no lines; the UI folds long EQUAL runs) |
| POST | `/repos/{owner}/{repo}/restore` | `{branch, commit, path?}`; whole commit or one file; creates a **new commit** on the branch (Write); 409 `WORKTREE_DIRTY` if your working copy is on that branch and dirty | 201 Commit |
| GET | `/repos/{owner}/{repo}/graph?limit=200` | for the commit graph (Harshini's UI) | `{commits: [{hash, parents, message, author, createdAt}], branches: [{name, head}]}` |

Commit = `{hash, treeHash, parents: [hash], author, message, createdAt}`

`createdAt` is the time stored inside the commit object. Services write commits with
`RepoObjectStores.writeCommit(...)` and save `StoredCommit.createdAt()` to `commits.created_at`, so the two always
match (millisecond precision). Don't rely on the column default. File paths go through
`RepoObjectStores.buildTree(...)`, which runs `PathValidator` first.

## U5 Branches: Aakriti

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/branches` | any reader, guests on public repos (built in U4's controller); default branch first | `[{name, head, isProtected, isDefault}]` |
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
| GET | `/repos/{owner}/{repo}/pulls?status=OPEN` | `status` = OPEN (default), MERGED, CLOSED or ALL; newest first; anyone who can read the repo | paged PR |
| POST | `/repos/{owner}/{repo}/pulls` | `{title, description?, source, target}` (Write); title <= 200, description <= 5000; branches must differ and exist | 201 PR |
| GET | `/repos/{owner}/{repo}/pulls/{number}` | `diff` = what the merge would bring in (merge base to source head), only while OPEN; merged PRs link to `mergeCommit` | `{pr, diff: [same as /diff], comments: [{id, author, body, createdAt}]}` |
| POST | `/repos/{owner}/{repo}/pulls/{number}/comments` | `{body}` (any role on the repo; PR must be OPEN) | 201 `{id, author, body, createdAt}` |
| POST | `/repos/{owner}/{repo}/pulls/{number}/approve` | not the author, PR must be OPEN; approving again records the source branch's current head; new commits on the source reset approvals (Write) | 204 |
| POST | `/repos/{owner}/{repo}/pulls/{number}/merge` | runs U6; approval not required, unless the target branch has the approval rule on: then 409 `APPROVAL_REQUIRED` until someone other than the author has approved the source's current head (Write) | `{result: UP_TO_DATE\|FAST_FORWARD\|MERGED\|CONFLICTS, commit?, sessionId?, pr}` |
| POST | `/repos/{owner}/{repo}/pulls/{number}/close` | PR must be OPEN; Write access or the PR's author | PR |

PR = `{number, title, description, author, source, target, status: OPEN|MERGED|CLOSED, approvals: [username], createdAt, closedAt?, mergeCommit?}`

There is no `APPROVED` status. Approving adds a row to `pr_approvals` and the PR stays `OPEN` (SRS v1.2: approval
recorded; required only when the target branch has the approval rule on). On `CONFLICTS` the PR stays `OPEN`; the user resolves through
`/merges/{sessionId}` and the PR becomes `MERGED` when that session completes (`MergeService` publishes a `BranchesMerged` event
that U8 listens to, so any merge of that branch pair marks the matching open PR merged).

## Branch protection, blame, revert: Harshini

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/repos/{repoId}/branches/protection` | names of branches with the approval rule on (any reader) | `["main"]` |
| PUT | `/repos/{repoId}/branches/protection` | `{name, requireApproval}` (Owner). On: no direct commits, merges, restores or undo on that branch; PR merges need an approval from someone other than the author | `{branch, requireApproval}` |
| GET | `/repos/{repoId}/blame?ref=&path=` | `ref` = branch or full hash; text files only; Read | `{ref, commit, path, lines: [{line, text, commit, author, time, message}]}` |
| POST | `/repos/{owner}/{repo}/commits/{hash}/revert` | `{branch}` (Write); new commit that undoes that commit on the branch; undoable | 201 Commit |

`branches.is_protected` (true for `main`) still only blocks deleting the branch. The approval rule is `branches.require_approval` (V2, off by default).

## Harshini: explore, dashboard, health

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/explore?q=&page=` | public repos only, matches name/description | paged Repo |
| GET | `/dashboard` | my repos, open PRs on my repos, recent commits | `{repos, openPulls, recentCommits}` |
| GET | `/health` | stack smoke test; `devAuth` is true while open-dev mode is on | `{status, db, devAuth}` |

## Questions to settle in the meeting
1. Do PR merges reuse `/merges` (one code path), or does U8 call the merge service directly? (Proposed: call the service directly, same result shape.)
2. ~~Restore = new commit or head move?~~ Built as a new commit (history is kept, undo steps back). Say so if the team wants the other.
3. Max upload size, max files per commit, max repo size: depends on V-01..V-09.
4. Who owns U10 (undo)? This file says Aakriti; the SRS v1.2 review assigned U10 and U11 to Shrejal (still to confirm).
5. ~~Which code marks the PR MERGED after a conflict session?~~ Settled: a `BranchesMerged` event from `MergeService`, no schema change.

# MiniGit API contract (draft v0.1, for team review)

Base path: `/api/v1`. JSON everywhere except file upload (`multipart/form-data`) and raw file download.
Auth: `Authorization: Bearer <jwt>` (30-min fixed expiry, no refresh). Hashes are 64-char SHA-256 hex.
Repos are addressed as `{owner}/{repo}` (username + repo name), like GitHub.

Status for review: **nothing here is final until all four of us OK it.** Change it by PR to this file.

## Errors (same shape for every endpoint)

```json
{ "status": 409, "code": "BRANCH_HEAD_MOVED", "message": "The branch moved since you started...",
  "fieldErrors": [], "timestamp": "2026-10-20T10:00:00Z" }
```

| HTTP | code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation failed; see `fieldErrors` |
| 400 | `INVALID_PATH` | `..`, absolute path, backslash, `.minigit/` etc. |
| 401 | `UNAUTHENTICATED` | Missing/expired token |
| 403 | `FORBIDDEN` | Role too low (Table 2) |
| 404 | `NOT_FOUND` | Also used for private repos the caller can't see (don't leak existence) |
| 409 | `BRANCH_HEAD_MOVED` | Commit on a branch whose head moved since checkout |
| 409 | `MERGE_IN_PROGRESS` | Action blocked by an open merge session |
| 409 | `NAME_TAKEN` | Repo/branch name already exists |
| 423 | `ACCOUNT_LOCKED` | 5 failed logins, locked 15 min |

Paged lists: `?page=0&size=20` → `{ "items": [...], "page": 0, "size": 20, "total": 57 }`.

## U1 Auth: Shrejal

| Method | Path | Body / notes | Response |
|---|---|---|---|
| POST | `/auth/register` | `{username, email, password}` | 201 `{id, username, email}` |
| POST | `/auth/login` | `{usernameOrEmail, password}` | 200 `{token, expiresAt, user}` |
| GET | `/auth/me` | | `{id, username, email}` |

No logout endpoint: client drops the token.

## U2 Repositories: Shrejal

| Method | Path | Body / notes | Response |
|---|---|---|---|
| POST | `/repos` | `{name, description?, visibility}`; creates `main` + empty initial commit | 201 Repo |
| GET | `/repos/mine` | repos I own or collaborate on | paged Repo |
| GET | `/repos/{owner}/{repo}` | | Repo |
| PATCH | `/repos/{owner}/{repo}` | `{name?, description?, visibility?}` (Owner) | Repo |
| DELETE | `/repos/{owner}/{repo}` | soft delete → recycle bin (Owner) | 204 |

Repo = `{id, owner, name, description, visibility, defaultBranch, myRole, createdAt, updatedAt}`

## U9 Collaborators: Shrejal

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/repos/{owner}/{repo}/collaborators` | | `[{userId, username, role}]` |
| PUT | `/repos/{owner}/{repo}/collaborators/{username}` | `{role: READ\|WRITE}` (Owner) | `{userId, username, role}` |
| DELETE | `/repos/{owner}/{repo}/collaborators/{username}` | (Owner) | 204 |

## U11 Recycle bin: Shrejal

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/recycle-bin` | my deleted repos + branches | `[{id, itemType, name, repo, deletedAt, expiresAt}]` |
| POST | `/recycle-bin/{id}/restore` | | 204 |
| DELETE | `/recycle-bin/{id}` | permanent delete now | 204 |

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
| POST | `/repos/{owner}/{repo}/pulls/{number}/approve` | not the author (Write) | 204 |
| POST | `/repos/{owner}/{repo}/pulls/{number}/merge` | runs U6; conflicts → 409 with `sessionId` | PR |
| POST | `/repos/{owner}/{repo}/pulls/{number}/close` | | PR |

PR = `{number, title, description, author, source, target, status: OPEN|MERGED|CLOSED, approvals: [username], createdAt, closedAt?}`

## Harshini: explore, dashboard, health

| Method | Path | Notes | Response |
|---|---|---|---|
| GET | `/explore?q=&page=` | public repos only, matches name/description | paged Repo |
| GET | `/dashboard` | my repos, open PRs on my repos, recent commits | `{repos, openPulls, recentCommits}` |
| GET | `/health` | stack smoke test | `{status, db}` |

## Questions to settle in the meeting
1. Do PR merges reuse `/merges` (one code path), or does U8 call the merge service directly? (Proposed: call the service directly, same result shape.)
2. Restore = new commit on the branch (proposed) or move the head back? Undo already covers "move back".
3. Max upload size, max files per commit, max repo size: depends on V-01..V-09.

# Member 3 (Aakriti) integration: branches, merge, conflicts, undo

Ported from `minigit-member3-final-integrated.zip` into `in.nitw.minigit`. Her bundled copy of the
storage engine, her pom/application.properties (`ddl-auto=update`) and both `MiniGitApplication`
classes were dropped. Everything runs on `RepoObjectStores` and the V1 tables.

## Packages
- `branch`: `BranchStore` (branches table, compare-and-set head moves), `CommitRows` (commits + commit_parents),
  `BranchService`/`BranchController`, `BranchNames`, `WorktreeSync`.
- `merge`: `MergeEngine` (pure logic), `MergeSession`, `FileConflict`, `MergeService`/`MergeController`.
- `undo`: `Command`, `HeadMoveCommand`, `UndoStack`, `UndoService` (one stack per branch).
- `access`: `RepoAccess` (current user + Read/Write/Owner check).

## Endpoints (all under /api/v1/repos/{repoId})
Branch names can contain "/", so they never go in the URL path.
- `GET /branches` (Read)
- `POST /branches` `{name, from}`, where from = branch name or 64-char commit hash (Write)
- `DELETE /branches?name=` (Write, 409 BRANCH_PROTECTED for main/protected)
- `POST /branches/checkout` `{name}`: the caller's working copy now matches the head exactly (Write)
- `POST /branches/undo` `{name}` / `GET /branches/undo?name=` (Write / Read)
- `POST /merges` `{source, target, expectedTargetHead?, message?}` returns status
  UP_TO_DATE | FAST_FORWARD | MERGED | CONFLICTS (+ session with conflicts)
- `GET /merges/{id}`, `POST /merges/{id}/resolve` `{path, choice, content}`,
  `POST /merges/{id}/finish` `{message}`, `POST /merges/{id}/abort`

Errors: 409 BRANCH_HEAD_MOVED, MERGE_IN_PROGRESS, MERGE_CONFLICT, NOTHING_TO_UNDO, BRANCH_PROTECTED.

## Hooks for other modules
- U3 commit / U4 restore: after moving a head call `undoService.record(repoId, branch, Kind.COMMIT|RESTORE, before, after)`.
- U8 PRs: call `mergeService.merge(...)`; status MERGED / FAST_FORWARD / UP_TO_DATE means mark the PR MERGED.

## Known gaps
- Merge sessions (`merge_sessions` / `merge_conflicts`) and undo stacks (`undo_actions`) are stored in the database and survive a restart. Sessions expire after 1 h.
- Branch delete is a hard delete; recycle bin (U11) should take over.
- Checkout doesn't check for uncommitted working-copy changes (that state belongs to U3).
- Public-repo read access for guests is not handled in `RepoAccess`.

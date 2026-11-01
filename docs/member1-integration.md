# Member 1 (Shrejal) integration: auth, repos, collaborators, recycle bin

Ported from `minigit-mem1.zip` onto the real backend. Her copy used package `com.minigit`, JPA entities with
`ddl-auto=update`, its own error handler, its own `@RepoAccess` and a sliding token. None of that came over: everything
runs on the V1 tables, `ApiException`/`ApiError`, `CurrentUser` and `access.RepoAccess`. Her logic (BCrypt, lockout,
name rules, recycle-bin restore and cleanup) and her pages are kept.

## Packages
- `auth`: `AuthController`, `AuthService` (register, login, lockout), `JwtService`, `JwtAuthFilter`, `UserRows`.
- `repo`: `RepoController`/`RepoService` (U2), `RepoNames`, `CollaboratorController` (U9).
- `recycle`: `RecycleBinService`/`RecycleBinController` (U11) and the daily cleanup job.
- Changed existing files: `SecurityConfig` (adds the JWT filter, 401/403 in the ApiError shape), `CurrentUser` (+`findId` for
  guests), `BranchStore`/`BranchService`/`BranchController` (branch delete is now a soft delete, see below).
- Frontend: `RegisterPage`, `NewRepoPage`, `RecycleBinPage`, `RepoSettingsPage` replace four placeholders in `App.jsx`.

## Turning real auth on
```
export MINIGIT_OPEN_DEV=false
export JWT_SECRET='<32+ random characters>'
```
The dev-login box then disappears by itself. The filter runs in both modes, so a real token also works while open-dev is on.

## Decisions that differ from her zip
- **Fixed 30-minute token** (SRS), not an idle timeout. Her `X-New-Token` refresh header is gone. The client already logs out at `expiresAt`.
- **Owner has an `OWNER` row** in `repo_permissions` (the V1 schema comment and `RepoAccess` expect it), not "no row".
  Roles are READ / WRITE / OWNER (SRS Table 2), not READ/WRITE/ADMIN. Only the owner manages collaborators, renames, deletes.
- Repos are addressed `{owner}/{repo}` per the contract; branch and merge endpoints keep `/repos/{repoId}/...`. The repo JSON carries `id`.
- Repo creation also writes `main` (protected) and the empty initial commit, through `RepoObjectStores.buildTree/writeCommit` and
  `CommitRows`, so the commit time in the object and in `commits.created_at` match.
- A repo or branch name that is in the recycle bin still counts as taken (the unique key covers it), and the error says so.

## Recycle bin and branches
`BranchStore.softDelete` sets `branches.deleted_at` and adds a `recycle_bin` row; `list`/`find` ignore deleted branches.
Restore clears `deleted_at`. Permanent delete removes the row (a repo: `DELETE` cascades through commits, branches, PRs, permissions
and the other bin rows, then the folder under the storage root is removed). Branch commits stay in the object store (shared, deduplicated).

## Known gaps
- `RepoAccess` still doesn't give guests or non-collaborators read access to **public** repos on the `/repos/{repoId}/...` endpoints
  (the `{owner}/{repo}` read does). Arisha's read endpoints need this.
- Merge sessions of a purged repo are removed with it (ON DELETE CASCADE).
- `DELETE /repos/{repoId}/branches?name=` and `/repos/{repoId}/...` differ from the contract's `/repos/{owner}/{repo}/branches/{name}`.
  Same for merges. Pick one before the frontend wires branches.
- No Java test covers the SQL in `RepoService`/`RecycleBinService`; the statements were run by hand against the V1 schema in MySQL 8.0.

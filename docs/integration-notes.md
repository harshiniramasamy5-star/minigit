# Integration notes (Harshini)

## Explore, dashboard, commit graph

- `GET /api/v1/explore?q=&page=&size=`: public, non-deleted repos. Search is case-insensitive on name/description; `%` and `_` are matched literally.
- `GET /api/v1/dashboard`: repos I own or collaborate on, open PRs on them, recent commits (the empty initial commit is skipped).
- Graph tab: `/<owner>/<repo>/graph`. Add `?demo=1` to see sample history without a backend.

## Dev login (temporary, until U1 auth is merged)

While the backend runs with `MINIGIT_OPEN_DEV=true` (the default), `/health` returns `devAuth: true`
and the login page shows a **Dev mode** box: type an existing username and you're in, no password.
The frontend then sends `X-Dev-User: <username>` instead of a token (`CurrentUser` already accepts it in open-dev only).

- The user must already exist. Dev login does not register anyone (that's U1). For local testing:
  ```sql
  INSERT INTO users (username, email, password_hash) VALUES ('harshini', 'harshini@dev.local', 'dev-no-login');
  ```
- When U1 lands and open-dev is switched off, the box disappears and any stored dev login is cleared on the next page load.
  No code needs to change. To delete it entirely later: `DevLogin` in `LoginPage.jsx`, `devLogin` in `AuthContext.jsx`,
  `DEV_USER_KEY` in `client.js`.

## Needs from teammates

- **Shrejal (U1):** the JWT filter must set an authenticated `Authentication` whose `getName()` is the **username**. `CurrentUser` relies on only that.
  Until auth exists (open-dev mode only):
  `curl -H "X-Dev-User: <username>" localhost:8080/api/v1/dashboard`
- **Shrejal (U2):** when creating a repo, also insert an `OWNER` row in `repo_permissions` (the schema comment expects it). Dashboard works either way.
- **Arisha (U4):** the graph calls `GET /repos/{owner}/{repo}/graph?limit=200` and expects
  `{ commits: [{hash, parents, message, author, createdAt}], branches: [{name, head}] }`, commits newest first.

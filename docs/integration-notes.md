# Integration notes (Harshini)

## Explore, dashboard, commit graph

- `GET /api/v1/explore?q=&page=&size=`: public, non-deleted repos. Search is case-insensitive on name/description; `%` and `_` are matched literally.
- `GET /api/v1/dashboard`: repos I own or collaborate on, open PRs on them, recent commits (the empty initial commit is skipped).
- Graph tab: `/<owner>/<repo>/graph`. Add `?demo=1` to see sample history without a backend.

## Dev login (off by default; for local demos only)

Open-dev mode is off by default, so real register and login (JWT) are used. If you start the backend with `MINIGIT_OPEN_DEV=true`, `/health` returns `devAuth: true`
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

- **Shrejal (U1, U2, U9, U11):** done, see `docs/member1-integration.md`. The JWT filter sets an `Authentication` whose `getName()` is the username
  and repo creation inserts the `OWNER` row. Set `MINIGIT_OPEN_DEV=false` and a real `JWT_SECRET` to turn real auth on.
- **Arisha (U4):** the graph calls `GET /repos/{owner}/{repo}/graph?limit=200` and expects
  `{ commits: [{hash, parents, message, author, createdAt}], branches: [{name, head}] }`, commits newest first.

## UI refresh (GitHub look)

- Dark/light theme: colours are CSS variables in `frontend/src/index.css`; the toggle is in the header (`lib/theme.js`).
- Dashboard (`/`) shows the contribution heatmap + activity overview from `GET /activity`. Add `?demo=1` to preview with sample data.
- Login/Register use `PasswordInput` (show/hide eye + Caps Lock hint).
- Graph tab shows every commit message with author, age, branch/merge pills. "Load older commits" raises `limit` up to 500.
- Java: `ContributionKind` (enum with behaviour) + `ContributionTally` replace the string switch in `ActivityReadQueries`.

# Integration notes (Harshini)

## Explore, dashboard, commit graph

- `GET /api/v1/explore?q=&page=&size=`: public, non-deleted repos. Search is case-insensitive on name/description; `%` and `_` are matched literally.
- `GET /api/v1/dashboard`: repos I own or collaborate on, open PRs on them, recent commits (the empty initial commit is skipped).
- Graph tab: `/<owner>/<repo>/graph`. Add `?demo=1` to see sample history without a backend.

## Needs from teammates

- **Shrejal (U1):** the JWT filter must set an authenticated `Authentication` whose `getName()` is the **username**. `CurrentUser` relies on only that.
  Until auth exists (open-dev mode only):
  `curl -H "X-Dev-User: <username>" localhost:8080/api/v1/dashboard`
- **Shrejal (U2):** when creating a repo, also insert an `OWNER` row in `repo_permissions` (the schema comment expects it). Dashboard works either way.
- **Arisha (U4):** the graph calls `GET /repos/{owner}/{repo}/graph?limit=200` and expects
  `{ commits: [{hash, parents, message, author, createdAt}], branches: [{name, head}] }`, commits newest first.

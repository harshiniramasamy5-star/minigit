# Working on MiniGit (Group II-19)

## Who owns what

| Member | Area | Use cases |
|---|---|---|
| Shrejal | Auth, repositories, collaborators, recycle bin | U1, U2, U9, U11 |
| Arisha | Working copy + commit, history/diff/restore, pull requests | U3, U4, U8 |
| Aakriti | Branches, merge + conflicts, undo | U5, U6, U7, U10 |
| Harshini | Setup, schema, React shell, explore, dashboard, commit graph | integration |

API shapes live in `docs/api-contract.md`. Change the contract by PR, and all four of us OK it.

## Git workflow

1. `git checkout main && git pull`
2. `git checkout -b feat/<short-name>` (e.g. `feat/branches-api`)
3. Commit small, push, open a PR into `main`.
4. CI must be green. One teammate reviews before merging.
5. Never push straight to `main`.

## Before you open a PR

```bash
cd backend && mvn verify          # all tests pass
cd ../frontend && npm run build   # frontend compiles
```

## Rules that keep us from breaking each other

- **Schema:** never edit `V1__init_schema.sql`. Add `V2__your_change.sql`, `V3__...` instead.
- **Errors:** throw `ApiException` (e.g. `ApiException.notFound("Branch")`); never return error JSON by hand.
- **Paths:** run every user-supplied file path through `PathValidator`.
- **Paged lists:** return `PageResponse` and clamp params with `PageResponse.clampPage/clampSize`.
- **Frontend calls:** use `api` from `src/api/client.js` (it adds the token and shows error toasts). Pass `{ silent: true }` when your page handles the error itself.
- **Your page:** replace your `<Placeholder>` route in `App.jsx`; don't restructure other people's routes.
- Don't commit `target/`, `node_modules/`, `dist/`, zips or `.env` files.

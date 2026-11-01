# MiniGit

A Git-inspired version control web app. CS1207 OOP Lab, NIT Warangal, Group II-19:
Shrejal Agrawal, Arisha Jadhav, Aakriti Shah, Harshini Ramasamy.

**Stack:** React 18 + Tailwind (Vite) · Java 17 + Spring Boot 3.3 · MySQL 8 · SHA-256 file object store

```
backend/    Spring Boot API   (in.nitw.minigit)
frontend/   React app         (Vite dev server proxies /api to :8080)
docs/       api-contract.md   <- read this before writing an endpoint
```

## Run it locally

You need JDK 17+, Maven 3.9+, Node 20+, MySQL 8.

**1. Database (once)**
```sql
CREATE DATABASE minigit;
CREATE USER 'minigit'@'localhost' IDENTIFIED BY 'minigit';
GRANT ALL ON minigit.* TO 'minigit'@'localhost';
```

**2. Backend** (Flyway creates all tables on first start)
```bash
cd backend
mvn spring-boot:run
# check: http://localhost:8080/api/v1/health  ->  {"status":"UP","db":"UP"}
```
Different DB password? Set env vars instead of editing the yml: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
Repo files go to `backend/minigit-data/repos` (change with `MINIGIT_STORAGE_ROOT`).

**3. Frontend**
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Until the auth module is merged, `MINIGIT_OPEN_DEV=true` (the default) leaves every endpoint open so we can all test.
Shrejal flips it to `false` in the U1 PR.

## Who owns what

| Member | Modules | Backend package | Frontend routes |
|---|---|---|---|
| Shrejal | U1 Auth, U2 Repos, U9 Collaborators, U11 Recycle bin | `auth`, `repo`, `permission`, `recyclebin` | `/login` `/register` `/new` `/recycle-bin` `/:owner/:repo/settings` |
| Arisha | U3 Commit, U4 History/Diff, U8 PRs | `storage`, `commit`, `diff`, `pullrequest` | `/:owner/:repo` `/commits` `/pulls` |
| Aakriti | U5 Branches, U6 Merge, U7 Conflicts, U10 Undo | `branch`, `merge`, `undo` | `/branches` `/merges/:sessionId` |
| Harshini | Setup, schema, React shell, dashboard, explore, commit graph, validation, UML | `common`, `config`, `dashboard`, `explore` | `/` `/explore` `/:owner/:repo/graph` |

Each module goes end to end: entity → repository → service → controller → React page → JUnit tests.

## Shared building blocks (use these, don't rewrite them)

**Backend**
- `ApiException`: throw from services, e.g. `throw ApiException.conflict("BRANCH_HEAD_MOVED", "...")`. The global handler turns it into the standard error JSON.
- `PathValidator.resolveInside(repoRoot, userPath)`: call before touching any file from a request. It blocks `..`, absolute paths, backslashes and `.minigit/`.
- `Path storageRoot` bean: inject it to find `<root>/<repoId>/...`.
- `Branch` entity: map `version` with `@Version`. A concurrent head move then becomes a 409 automatically.
- `PasswordEncoder` bean is BCrypt cost 10.

**Frontend**
- `import { api } from '../api/client'`: adds the JWT, and shows an error toast on failure. Pass `{ silent: true }` to handle the error yourself (e.g. a 409 dialog).
- `useToast()` gives `toast.success('Branch created')`.
- `useAuth()` gives `{ user, login, logout }`.
- Tailwind tokens: `commit` (primary), `merged`, `conflict`, `danger`, `ink`, `muted`, `line`, `paper`. Classes: `btn-primary`, `btn-secondary`, `btn-danger`, `input`, `hash`.
- Routes are in `src/App.jsx`; add your page there.

## Git workflow

1. `main` is protected: no direct pushes, PR only, 1 approval from someone else.
2. Branch names: `feat/<module>-<thing>` (e.g. `feat/merge-three-way`), `fix/<thing>`.
3. Pull `main` before you start and rebase before you open the PR.
4. Schema changes go in a **new** file `backend/src/main/resources/db/migration/V<next>__what.sql`. Never edit a merged migration.
5. Changing an endpoint? Update `docs/api-contract.md` in the same PR.
6. PR must build: `mvn test` and `npm run build`.

## Schema

`V1__init_schema.sql` has 14 tables covering every use case. Object contents live on disk; MySQL holds metadata.
`src/test/resources/schema-smoke.sql` runs the full flow: create repo with an empty initial commit, branch, commit, PR, approve, merge commit with two parents, undo entry, branch to recycle bin, purge. It also has the recursive ancestor query merge needs.

# MiniGit mini demo (what works today)

Shows the parts that are already built: the frontend shell, dev login, explore, dashboard, health and the
commit graph. Everything else is a placeholder page labelled with its owner until U1–U11 land.

## 1. Start the stack (3 terminals, from the repo root)

```bash
# Terminal 1: MySQL
docker compose up -d

# Terminal 2: backend (Flyway creates the tables on first start)
cd backend && mvn spring-boot:run          # wait for "Started MiniGitApplication"

# Terminal 3: frontend
cd frontend && npm install && npm run dev  # http://localhost:5173
```

## 2. Load the demo data (once the backend has started, so the tables exist)

```bash
docker compose exec -T mysql mysql -uminigit -pminigit minigit < docs/demo-seed.sql
```

Using Homebrew MySQL instead of Docker: `mysql -uminigit -pminigit minigit < docs/demo-seed.sql`.

Safe to run again. It adds users harshini, arisha, aakriti and shrejal, six repos (one private to you, one private
to Shrejal that you can read, one deleted), commits, branches and three PRs (two open, one closed).

## 3. Click through

| Step | Where | What to point out |
|---|---|---|
| 1 | `localhost:5173/explore` (logged out) | 3 public repos. The private ones and the deleted `old-prototype` don't show. Search `merge` finds `merge-lab`; search `%` finds nothing (matched literally). |
| 2 | Log in → Dev mode box → `harshini` | Works because `/health` reports `devAuth: true`. No password until U1. |
| 3 | Dashboard | 4 repos with your role: OWNER on 2, READ on Shrejal's private repo and Arisha's repo. |
| 4 | Dashboard → open PRs | #1 and #2 on `minigit-demo`. The closed #3 is hidden. |
| 5 | Dashboard → recent commits | 9 commits across repos, newest first. The empty "Initial commit" of each repo is skipped. |
| 6 | `localhost:5173/harshini/minigit-demo/graph?demo=1` | Commit graph with branches and a merge (sample data until Arisha's `/graph` endpoint exists). |
| 7 | Other repo tabs (Files, Commits, Branches, PRs, Settings) | Placeholder pages showing who owns each use case. That's the route map for the team. |
| 8 | `localhost:8080/api/v1/health` | `{status, db, devAuth}`: the stack smoke test. |

## Reset

```bash
docker compose down -v && docker compose up -d   # wipes the database volume
```

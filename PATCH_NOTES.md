# M4 patch: explore + dashboard API, commit graph

New
- backend common/PageResponse, readmodel/{LikePattern, ReadModels, RepoReadQueries, CurrentUser, ReadModelController}
- backend tests: PageResponseTest, LikePatternTest
- frontend lib/layoutGraph.js (+ layoutGraph.check.mjs: `node frontend/src/lib/layoutGraph.check.mjs`)
- frontend components/CommitGraph.jsx, pages/GraphPage.jsx (add ?demo=1 to see sample history)

Changed
- frontend App.jsx: graph route uses GraphPage
- frontend DashboardPage.jsx: commit/PR rows show repo, unique keys

For Shrejal (U1): CurrentUser needs the JWT filter to set an Authentication whose getName() is the username.
Until then, with open-dev on: curl -H "X-Dev-User: <username>" localhost:8080/api/v1/dashboard
For Arisha (U4): GraphPage calls GET /repos/{owner}/{repo}/graph?limit=200, shape as in api-contract.md.

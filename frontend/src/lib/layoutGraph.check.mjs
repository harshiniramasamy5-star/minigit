// Run: node src/lib/layoutGraph.check.mjs   (CI runs this; exits 1 on any failure)
import { layoutGraph } from './layoutGraph.js'

let fail = 0
const ok = (c, m) => { if (!c) { fail++; console.log('FAIL', m) } }

// 1. Linear history stays in one lane
let g = layoutGraph([{ hash: 'C', parents: ['B'] }, { hash: 'B', parents: ['A'] }, { hash: 'A', parents: [] }])
ok(g.rows.every((r) => r.col === 0) && g.lanes === 1, 'linear stays in one lane')
ok(g.rows[2].segments.length === 1, 'root has only the incoming line')

// 2. Feature branch merged into main: main keeps lane 0
g = layoutGraph([
  { hash: 'M', parents: ['B', 'F'] },
  { hash: 'F', parents: ['A'] },
  { hash: 'B', parents: ['A'] },
  { hash: 'A', parents: [] },
])
const col = Object.fromEntries(g.rows.map((r) => [r.commit.hash, r.col]))
ok(col.M === 0 && col.B === 0 && col.F === 1 && col.A === 0, 'merge layout ' + JSON.stringify(col))
ok(g.lanes === 2, 'two lanes wide')
ok(g.rows[2].segments.some((s) => s.x1 === 1 && s.y1 === 0 && s.x2 === 0 && s.y2 === 1), 'feature lane joins main above A')
ok(g.rows[3].segments.length === 1 && g.rows[3].segments[0].x1 === 0, 'A has one incoming line in lane 0')

// 3. Two unmerged tips from the same commit
g = layoutGraph([{ hash: 'X', parents: ['A'] }, { hash: 'Y', parents: ['A'] }, { hash: 'A', parents: [] }])
ok(g.rows[0].col === 0 && g.rows[1].col === 1 && g.rows[2].col === 0, 'two tips -> two lanes')

// 4. Parent cut off by ?limit: line runs off the bottom, no crash
g = layoutGraph([{ hash: 'Z', parents: ['missing'] }])
ok(g.rows[0].segments.some((s) => s.y2 === 1), 'dangling parent draws to bottom')

// 5. Empty history
g = layoutGraph([])
ok(g.rows.length === 0 && g.lanes === 0, 'empty')

if (fail) { console.log(`${fail} failure(s)`); process.exit(1) }
console.log('layoutGraph: all checks pass')

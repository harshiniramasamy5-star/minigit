import { layoutGraph } from './layoutGraph.js'
let fail = 0
const ok = (c, m) => { if (!c) { fail++; console.log('FAIL', m) } }

// 1. Linear: C -> B -> A(root): all in lane 0
let g = layoutGraph([{hash:'C',parents:['B']},{hash:'B',parents:['A']},{hash:'A',parents:[]}])
ok(g.rows.every(r => r.col === 0) && g.lanes === 1, 'linear stays in one lane')
ok(g.rows[2].segments.length === 1, 'root has only the incoming line')

// 2. Branch + merge: M(merge of F into main) ; F (feature) ; B (main) ; A root
//    M parents [B, F]; F parents [A]; B parents [A]
g = layoutGraph([
  {hash:'M',parents:['B','F']},
  {hash:'F',parents:['A']},
  {hash:'B',parents:['A']},
  {hash:'A',parents:[]},
])
const col = Object.fromEntries(g.rows.map(r => [r.commit.hash, r.col]))
ok(col.M === 0 && col.B === 0 && col.F === 1 && col.A === 0, 'merge layout ' + JSON.stringify(col))
ok(g.lanes === 2, 'two lanes wide')
const rowA = g.rows[3]
ok(g.rows[2].segments.some(s => s.x1 === 1 && s.y1 === 0 && s.x2 === 0 && s.y2 === 1), 'feature lane joins main above A')
ok(rowA.segments.length === 1 && rowA.segments[0].x1 === 0, 'A has one incoming line in lane 0')

// 3. Two branch tips not yet merged: X(feat) and Y(main) both from A
g = layoutGraph([{hash:'X',parents:['A']},{hash:'Y',parents:['A']},{hash:'A',parents:[]}])
ok(g.rows[0].col === 0 && g.rows[1].col === 1 && g.rows[2].col === 0, 'two tips -> two lanes')

// 4. Parent outside the window (limit cut it off): lane just runs off the bottom, no crash
g = layoutGraph([{hash:'Z',parents:['missing']}])
ok(g.rows[0].segments.some(s => s.y2 === 1), 'dangling parent draws to bottom')

// 5. Empty
g = layoutGraph([]); ok(g.rows.length === 0 && g.lanes === 0, 'empty')

console.log(fail ? fail + ' failures' : 'layoutGraph: all checks pass')

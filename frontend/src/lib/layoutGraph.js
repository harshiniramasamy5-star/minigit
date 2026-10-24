// Lane layout for the commit graph (pure function, no React, so it is easy to test).
//
// Input:  commits newest-first, each { hash, parents: [hash, ...] }  (GET /graph shape)
// Output: { rows, lanes } where each row is
//   { commit, col, segments: [{ x1, y1, x2, y2, lane }] }
// Segment coordinates are in "lane units" inside one row: x = lane index, y = 0 (top of row),
// 0.5 (the dot) or 1 (bottom of row). The component scales them to pixels.
//
// How it works: `lanes[i]` holds the hash that lane i is waiting to reach.
// For each commit we find its lane, close any other lanes that were also waiting for it
// (branches joining), then hand its parents to lanes: first parent continues straight down,
// extra parents (merge commits) curve out to an existing or new lane.

export function layoutGraph(commits) {
  const lanes = []
  const rows = []
  let width = 0

  for (const commit of commits) {
    const before = lanes.slice()
    let col = before.indexOf(commit.hash)
    if (col === -1) col = freeLane(lanes)   // branch tip not seen yet: start a new lane

    const segments = []

    // Lanes that were waiting for this commit: draw them into the dot and free them.
    before.forEach((h, j) => {
      if (h !== commit.hash) return
      segments.push({ x1: j, y1: 0, x2: col, y2: 0.5, lane: j })
      if (j !== col) lanes[j] = null
    })
    lanes[col] = null

    // Parents.
    ;(commit.parents ?? []).forEach((p, idx) => {
      let k = lanes.indexOf(p)
      if (idx === 0 && k > col && lanes[col] === null) {
        // Another lane (to the right) is already heading to our first parent.
        // Pull it into our lane so shared history stays on the left, like `git log --graph`.
        lanes[k] = null
        lanes[col] = p
        segments.push({ x1: k, y1: 0, x2: col, y2: 1, lane: k })
        k = col
      } else if (k === -1) {
        k = idx === 0 && lanes[col] === null ? col : freeLane(lanes)
        lanes[k] = p
      }
      segments.push({ x1: col, y1: 0.5, x2: k, y2: 1, lane: k === col ? col : Math.max(k, col) })
    })

    // Everything else just passes through this row.
    before.forEach((h, j) => {
      if (h && h !== commit.hash && lanes[j] === h) {
        segments.push({ x1: j, y1: 0, x2: j, y2: 1, lane: j })
      }
    })

    trimTrailing(lanes)
    width = Math.max(width, col + 1, before.length, lanes.length)
    rows.push({ commit, col, segments })
  }

  return { rows, lanes: width }
}

function freeLane(lanes) {
  const i = lanes.indexOf(null)
  if (i !== -1) return i
  lanes.push(null)
  return lanes.length - 1
}

function trimTrailing(lanes) {
  while (lanes.length && lanes[lanes.length - 1] === null) lanes.pop()
}

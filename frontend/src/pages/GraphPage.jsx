import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import CommitGraph from '../components/CommitGraph'

const PAGE = 200            // matches the backend default
const MAX = 500             // backend ceiling (ActivityReadQueries.MAX_GRAPH_LIMIT)

// U4 UI (Harshini). Data: GET /repos/{owner}/{repo}/graph?limit=200 (Arisha's endpoint).
// Add ?demo=1 to the URL to see sample history (for screenshots and demos).
export default function GraphPage() {
  const { owner, repo } = useParams()
  const [params] = useSearchParams()
  const demo = params.get('demo') === '1'
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [limit, setLimit] = useState(PAGE)

  // switching repo starts again from the first page
  useEffect(() => { setLimit(PAGE) }, [owner, repo])

  useEffect(() => {
    if (demo) { setData(DEMO); setError(null); return }
    setError(null)
    api.get(`/repos/${owner}/${repo}/graph`, { params: { limit }, silent: true })
      .then((res) => setData(res.data))
      .catch(setError)
  }, [owner, repo, demo, limit])

  if (error) {
    return (
      <p className="text-sm text-muted">
        {error.status === 404
          ? "No history to show. This repository doesn't exist or you don't have access to it."
          : error.message}
      </p>
    )
  }
  if (!data) return <p className="text-sm text-muted">Loading history…</p>
  if (data.commits.length === 0) return <p className="text-sm text-muted">No commits yet.</p>

  const mayHaveMore = !demo && data.commits.length >= limit && limit < MAX

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted">
        <span><strong className="text-ink">{data.commits.length}</strong> {data.commits.length === 1 ? 'commit' : 'commits'}</span>
        <span><strong className="text-ink">{data.branches.length}</strong> {data.branches.length === 1 ? 'branch' : 'branches'}</span>
        <span className="flex items-center gap-3 text-xs">
          <Legend dot="bg-commit" label="commit" />
          <Legend dot="border-2 border-merged bg-surface" label="merge commit" />
        </span>
        {demo && <span className="text-xs">Showing sample data (demo mode).</span>}
      </div>
      <CommitGraph commits={data.commits} branches={data.branches} />
      {mayHaveMore && (
        <div className="mt-4 text-center">
          <button className="btn-secondary" onClick={() => setLimit((l) => Math.min(MAX, l + PAGE))}>Load older commits</button>
        </div>
      )}
    </div>
  )
}

function Legend({ dot, label }) {
  return <span className="inline-flex items-center gap-1.5"><span className={`inline-block h-2.5 w-2.5 rounded-full ${dot}`} />{label}</span>
}

const h = (c) => c.repeat(64)
const DEMO = {
  branches: [{ name: 'main', head: h('9') }, { name: 'feat/undo', head: h('8') }],
  commits: [
    { hash: h('9'), parents: [h('7'), h('6')], message: 'Merge feat/graph into main', author: 'harshini', createdAt: '2026-10-24T10:00:00Z' },
    { hash: h('8'), parents: [h('5')], message: 'Undo stack per branch', author: 'aakriti', createdAt: '2026-10-24T09:00:00Z' },
    { hash: h('7'), parents: [h('4')], message: 'Fix 409 on moved branch head', author: 'arisha', createdAt: '2026-10-23T18:00:00Z' },
    { hash: h('6'), parents: [h('4')], message: 'Commit graph lanes', author: 'harshini', createdAt: '2026-10-23T15:00:00Z' },
    { hash: h('5'), parents: [h('4')], message: 'Branch create/delete', author: 'aakriti', createdAt: '2026-10-23T12:00:00Z' },
    { hash: h('4'), parents: [h('3')], message: 'Recycle bin', author: 'shrejal', createdAt: '2026-10-22T12:00:00Z' },
    { hash: h('3'), parents: [h('2')], message: 'Add README', author: 'harshini', createdAt: '2026-10-21T12:00:00Z' },
    { hash: h('2'), parents: [], message: 'Initial commit', author: 'harshini', createdAt: '2026-10-20T12:00:00Z' },
  ],
}

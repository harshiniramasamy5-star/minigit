import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import CommitGraph from '../components/CommitGraph'

// U4 UI (Harshini). Data: GET /repos/{owner}/{repo}/graph?limit=200 (Arisha's endpoint).
// Add ?demo=1 to the URL to see sample history (for screenshots and demos).
export default function GraphPage() {
  const { owner, repo } = useParams()
  const [params] = useSearchParams()
  const demo = params.get('demo') === '1'
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (demo) { setData(DEMO); setError(null); return }
    setData(null); setError(null)
    api.get(`/repos/${owner}/${repo}/graph`, { params: { limit: 200 }, silent: true })
      .then((res) => setData(res.data))
      .catch(setError)
  }, [owner, repo, demo])

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

  return (
    <div>
      {demo && <p className="mb-3 text-xs text-muted">Showing sample data (demo mode).</p>}
      <CommitGraph commits={data.commits} branches={data.branches} />
    </div>
  )
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

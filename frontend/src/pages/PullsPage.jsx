import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useRepo, when } from '../lib/useRepo'

const TABS = [['OPEN', 'Open'], ['MERGED', 'Merged'], ['CLOSED', 'Closed']]
export const BADGE = {
  OPEN: 'border-commit/40 text-commit',
  MERGED: 'border-merged/40 text-merged',
  CLOSED: 'border-line text-muted',
}

// U8: pull requests of a repository.
export default function PullsPage() {
  const { owner, repo: name } = useParams()
  const { canWrite } = useRepo(owner, name)
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const status = params.get('status') || 'OPEN'
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    setData(null); setError(null)
    api.get(`/repos/${owner}/${name}/pulls`, { params: { status }, silent: true })
      .then((r) => setData(r.data)).catch(setError)
  }, [owner, name, status])

  return (
    <div>
      <div className="flex items-center gap-2">
        {TABS.map(([value, label]) => (
          <button key={value} onClick={() => setParams({ status: value })}
            className={`rounded-md px-3 py-1.5 text-sm ${status === value ? 'bg-commit-soft font-medium text-ink' : 'text-muted hover:text-ink'}`}>
            {label}
          </button>
        ))}
        {user && canWrite && <Link to="new" className="btn-primary ml-auto">New pull request</Link>}
      </div>

      {error && <p className="mt-6 text-sm text-danger">{error.message}</p>}
      {!data && !error && <p className="mt-6 text-sm text-muted">Loading…</p>}
      {data && data.items.length === 0 && (
        <div className="mt-4 rounded-lg border border-line bg-white p-6 text-sm text-muted">No {status.toLowerCase()} pull requests.</div>
      )}
      {data && data.items.length > 0 && (
        <ul className="mt-4 divide-y divide-line rounded-lg border border-line bg-white">
          {data.items.map((pr) => (
            <li key={pr.number} className="flex items-center gap-3 p-3">
              <div className="min-w-0 flex-1">
                <Link to={String(pr.number)} className="block truncate font-medium hover:underline">{pr.title}</Link>
                <p className="text-xs text-muted">
                  #{pr.number} · {pr.author} · {when(pr.createdAt)} · <span className="font-mono">{pr.source} → {pr.target}</span>
                </p>
              </div>
              {pr.approvals.length > 0 && <span className="text-xs text-commit">{pr.approvals.length} approval{pr.approvals.length === 1 ? '' : 's'}</span>}
              <span className={`rounded border px-1.5 text-xs ${BADGE[pr.status]}`}>{pr.status}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

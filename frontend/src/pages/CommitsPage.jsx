import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { shortHash, useRepo, when } from '../lib/useRepo'

const SIZE = 20

// U4: commit history of a branch, newest first.
export default function CommitsPage() {
  const { owner, repo: name } = useParams()
  const { repo, error: repoError } = useRepo(owner, name)
  const [params, setParams] = useSearchParams()
  const [branches, setBranches] = useState([])
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const branch = params.get('branch') || repo?.defaultBranch
  const page = Number(params.get('page') || 0)

  useEffect(() => {
    api.get(`/repos/${owner}/${name}/branches`, { silent: true }).then((r) => setBranches(r.data)).catch(() => {})
  }, [owner, name])

  useEffect(() => {
    if (!branch) return
    setData(null)
    setError(null)
    api.get(`/repos/${owner}/${name}/commits`, { params: { branch, page, size: SIZE }, silent: true })
      .then((r) => setData(r.data)).catch(setError)
  }, [owner, name, branch, page])

  if (repoError) return <p className="text-sm text-danger">{repoError.message}</p>
  const go = (patch) => setParams({ ...(params.get('branch') ? { branch: params.get('branch') } : {}), ...patch })

  return (
    <div>
      <div className="flex items-center gap-3">
        <label htmlFor="branch" className="text-sm font-medium">Branch</label>
        <select id="branch" className="input w-56" value={branch ?? ''} onChange={(e) => setParams({ branch: e.target.value })}>
          {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
        </select>
        {data && <span className="text-sm text-muted">{data.total} commit{data.total === 1 ? '' : 's'}</span>}
      </div>

      {error && <p className="mt-6 text-sm text-danger">{error.message}</p>}
      {!data && !error && <p className="mt-6 text-sm text-muted">Loading…</p>}
      {data && (
        <>
          <ul className="mt-4 divide-y divide-line rounded-lg border border-line bg-white">
            {data.items.map((c) => (
              <li key={c.hash} className="flex items-center gap-3 p-3">
                <div className="min-w-0 flex-1">
                  <Link to={`${c.hash}${params.get('branch') ? `?branch=${encodeURIComponent(params.get('branch'))}` : ''}`}
                    className="block truncate font-medium hover:underline">{c.message.split('\n')[0]}</Link>
                  <p className="text-xs text-muted">{c.author} · {when(c.createdAt)}{c.parents.length > 1 ? ' · merge' : ''}</p>
                </div>
                <Link to={c.hash} className="hash hover:underline">{shortHash(c.hash)}</Link>
              </li>
            ))}
          </ul>
          <div className="mt-4 flex gap-2">
            <button className="btn-secondary" disabled={page === 0} onClick={() => go({ page: page - 1 })}>Newer</button>
            <button className="btn-secondary" disabled={(page + 1) * SIZE >= data.total} onClick={() => go({ page: page + 1 })}>Older</button>
          </div>
        </>
      )}
    </div>
  )
}

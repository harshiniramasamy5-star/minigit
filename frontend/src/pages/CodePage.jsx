import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import WorkingCopyPanel from '../components/WorkingCopyPanel'
import { useRepo } from '../lib/useRepo'

// U3 + U4: browse the committed files of a branch (anyone who can read the repo) and, with Write access,
// edit your working copy and commit.
export default function CodePage() {
  const { owner, repo: name } = useParams()
  const { repo, error: repoError, canWrite } = useRepo(owner, name)
  const [params, setParams] = useSearchParams()
  const [branches, setBranches] = useState([])
  const [entries, setEntries] = useState(null)
  const [file, setFile] = useState(null)       // { path, text } or { path, binary: true }
  const [error, setError] = useState(null)
  const [reload, setReload] = useState(0)
  const base = `/repos/${owner}/${name}`
  const ref = params.get('ref') || repo?.defaultBranch
  const path = params.get('path') || ''
  const open = params.get('file') || ''

  const loadBranches = useCallback(() => {
    api.get(`${base}/branches`, { silent: true }).then((r) => setBranches(r.data)).catch(() => {})
  }, [base])
  useEffect(loadBranches, [loadBranches, reload])

  useEffect(() => {
    if (!ref) return
    setEntries(null); setError(null)
    api.get(`${base}/tree`, { params: { ref, path }, silent: true })
      .then((r) => setEntries(r.data)).catch(setError)
  }, [base, ref, path, reload])

  useEffect(() => {
    setFile(null)
    if (!open || !ref) return
    api.get(`${base}/blob`, { params: { ref, path: open }, responseType: 'text', transformResponse: (r) => r, silent: true })
      .then((r) => setFile({ path: open, text: r.headers['content-type']?.startsWith('text/') ? r.data : null }))
      .catch(() => setFile({ path: open, text: null }))
  }, [base, ref, open, reload])

  if (repoError) return <p className="text-sm text-danger">{repoError.message}</p>
  if (!repo) return <p className="text-sm text-muted">Loading…</p>

  const to = (patch) => setParams({ ...(params.get('ref') ? { ref: params.get('ref') } : {}), ...patch })
  const crumbs = path ? path.split('/') : []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <label htmlFor="ref" className="text-sm font-medium">Branch</label>
        <select id="ref" className="input w-56" value={ref} onChange={(e) => setParams({ ref: e.target.value })}>
          {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
        </select>
        <Link to={`commits${params.get('ref') ? `?branch=${encodeURIComponent(params.get('ref'))}` : ''}`}
          className="text-sm text-commit hover:underline">View history</Link>
      </div>

      <section aria-label="Files">
        <nav className="mb-2 flex flex-wrap gap-1 text-sm" aria-label="Folder">
          <button className="text-commit hover:underline" onClick={() => to({})}>{name}</button>
          {crumbs.map((c, i) => (
            <span key={i} className="flex gap-1">
              <span className="text-muted">/</span>
              <button className="text-commit hover:underline" onClick={() => to({ path: crumbs.slice(0, i + 1).join('/') })}>{c}</button>
            </span>
          ))}
        </nav>
        {error && <p className="text-sm text-danger">{error.message}</p>}
        {!entries && !error && <p className="text-sm text-muted">Loading…</p>}
        {entries && entries.length === 0 && (
          <div className="rounded-lg border border-line bg-white p-6 text-sm text-muted">
            {path ? 'This folder is empty.' : canWrite
              ? 'No files yet. Add one in your working copy below, then commit.'
              : 'This repository has no files yet.'}
          </div>
        )}
        {entries && entries.length > 0 && (
          <ul className="divide-y divide-line rounded-lg border border-line bg-white text-sm">
            {entries.map((e) => (
              <li key={e.name}>
                <button className="flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-paper"
                  onClick={() => to(e.type === 'DIR'
                    ? { path: path ? `${path}/${e.name}` : e.name }
                    : { ...(path ? { path } : {}), file: path ? `${path}/${e.name}` : e.name })}>
                  <span aria-hidden className="w-4 text-muted">{e.type === 'DIR' ? '▸' : ' '}</span>
                  <span className={e.type === 'DIR' ? 'font-medium' : 'font-mono'}>{e.name}</span>
                  {e.type === 'FILE' && <span className="ml-auto text-xs text-muted">{e.size} B</span>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {file && (
        <section className="overflow-hidden rounded-lg border border-line bg-white" aria-label={`File ${file.path}`}>
          <header className="flex items-center border-b border-line bg-paper px-3 py-2 text-sm">
            <span className="font-mono">{file.path}</span>
            <button className="ml-auto text-muted hover:text-ink" onClick={() => to(path ? { path } : {})}>Close</button>
          </header>
          {file.text === null
            ? <p className="p-3 text-sm text-muted">Binary file, no preview.</p>
            : <pre className="overflow-x-auto p-3 font-mono text-xs leading-5">{file.text}</pre>}
        </section>
      )}

      {canWrite && (
        <WorkingCopyPanel owner={owner} name={name} branches={branches} onCommitted={() => setReload((n) => n + 1)} />
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import DiffView from '../components/DiffView'
import { useToast } from '../context/ToastContext'
import { shortHash, useRepo, when } from '../lib/useRepo'

// U4: one commit with what it changed, plus restore (whole commit or one file) for people with Write access.
export default function CommitDetailPage() {
  const { owner, repo: name, hash } = useParams()
  const { repo, canWrite } = useRepo(owner, name)
  const toast = useToast()
  const base = `/repos/${owner}/${name}`
  const [detail, setDetail] = useState(null)
  const [diff, setDiff] = useState(null)
  const [error, setError] = useState(null)
  const [branches, setBranches] = useState([])
  const [target, setTarget] = useState('')
  const [armed, setArmed] = useState(null)      // 'ALL' or a path: second click confirms
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setDetail(null); setDiff(null); setError(null)
    api.get(`${base}/commits/${hash}`, { silent: true }).then(async (r) => {
      setDetail(r.data)
      const parent = r.data.commit.parents[0]
      const d = await api.get(`${base}/diff`, { params: { from: parent ?? '', to: hash }, silent: true })
      setDiff(d.data)
    }).catch(setError)
  }, [base, hash])

  useEffect(() => {
    api.get(`${base}/branches`, { silent: true }).then((r) => {
      setBranches(r.data)
      setTarget((t) => t || r.data[0]?.name || '')
    }).catch(() => {})
  }, [base])

  const restore = async (path) => {
    setBusy(true)
    try {
      const { data } = await api.post(`${base}/restore`, { branch: target, commit: hash, path: path === 'ALL' ? undefined : path })
      toast.success(`Restored on ${target} as ${shortHash(data.hash)}`)
      setArmed(null)
    } catch {
      // the API client showed the error (e.g. uncommitted changes on that branch)
    } finally {
      setBusy(false)
    }
  }

  if (error) return <p className="text-sm text-danger">{error.message}</p>
  if (!detail) return <p className="text-sm text-muted">Loading…</p>
  const c = detail.commit

  const restoreButton = (key, label) => armed === key ? (
    <span className="inline-flex gap-2">
      <button className="btn-danger px-2 py-1 text-xs" disabled={busy} onClick={() => restore(key)}>Restore on {target}</button>
      <button className="btn-secondary px-2 py-1 text-xs" onClick={() => setArmed(null)}>Cancel</button>
    </span>
  ) : (
    <button className="btn-secondary px-2 py-1 text-xs" onClick={() => setArmed(key)}>{label}</button>
  )

  return (
    <div className="space-y-6">
      <div>
        <Link to=".." relative="path" className="text-sm text-commit hover:underline">← All commits</Link>
        <h2 className="mt-2 whitespace-pre-wrap text-lg font-semibold">{c.message}</h2>
        <p className="mt-1 text-sm text-muted">{c.author} · {when(c.createdAt)}</p>
        <p className="mt-1 text-xs text-muted">
          <span className="hash">{c.hash}</span>
          {c.parents.map((p) => <span key={p}> · parent <Link to={`../${p}`} relative="path" className="hash hover:underline">{shortHash(p)}</Link></span>)}
        </p>
      </div>

      {canWrite && repo && (
        <section className="rounded-lg border border-line bg-white p-4" aria-labelledby="restore">
          <h3 id="restore" className="text-sm font-semibold">Restore</h3>
          <p className="mt-1 text-xs text-muted">Creates a new commit on the branch with the files from this version. History is kept, and you can undo it.</p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <label htmlFor="restore-branch" className="text-sm">Branch</label>
            <select id="restore-branch" className="input w-56" value={target} onChange={(e) => { setTarget(e.target.value); setArmed(null) }}>
              {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
            </select>
            {restoreButton('ALL', 'Restore all files')}
          </div>
        </section>
      )}

      <section>
        <h3 className="mb-3 text-sm font-semibold">{detail.files.length} file{detail.files.length === 1 ? '' : 's'} changed</h3>
        {!diff && <p className="text-sm text-muted">Loading changes…</p>}
        {diff && <DiffView files={diff} actions={canWrite ? (f) => restoreButton(f.path, 'Restore this file') : undefined} />}
      </section>
    </div>
  )
}

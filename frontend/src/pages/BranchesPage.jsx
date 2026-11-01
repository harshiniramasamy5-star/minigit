import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useToast } from '../context/ToastContext'
import { shortHash, useRepo } from '../lib/useRepo'

// U5 + U6 + U10: list, create, delete (to the recycle bin), undo and merge branches.
export default function BranchesPage() {
  const { owner, repo: name } = useParams()
  const { repo, canWrite } = useRepo(owner, name)
  const toast = useToast()
  const navigate = useNavigate()
  const [branches, setBranches] = useState(null)
  const [error, setError] = useState(null)
  const [create, setCreate] = useState({ name: '', from: '' })
  const [merge, setMerge] = useState({ source: '', target: '' })
  const [undoFor, setUndoFor] = useState(null)       // { branch, entries }
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [busy, setBusy] = useState(false)
  const api2 = repo ? `/repos/${repo.id}` : null

  const load = useCallback(() => {
    api.get(`/repos/${owner}/${name}/branches`, { silent: true }).then((r) => {
      setBranches(r.data)
      setCreate((c) => ({ ...c, from: c.from || r.data[0]?.name || '' }))
      setMerge((m) => ({
        source: m.source || r.data.find((b) => !b.isDefault)?.name || '',
        target: m.target || r.data.find((b) => b.isDefault)?.name || r.data[0]?.name || '',
      }))
    }).catch(setError)
  }, [owner, name])
  useEffect(load, [load])

  const run = async (fn) => {
    setBusy(true)
    try { await fn() } catch { /* the API client showed the error toast */ } finally { setBusy(false) }
  }

  const createBranch = (e) => {
    e.preventDefault()
    return run(async () => {
      await api.post(`${api2}/branches`, { name: create.name.trim(), from: create.from })
      toast.success(`Created ${create.name.trim()}`)
      setCreate({ name: '', from: create.from })
      load()
    })
  }

  const deleteBranch = (b) => run(async () => {
    await api.delete(`${api2}/branches`, { params: { name: b.name } })
    toast.success(`${b.name} moved to the recycle bin. You can restore it for 7 days.`)
    setConfirmDelete(null)
    load()
  })

  const askUndo = (b) => run(async () => {
    const { data } = await api.get(`${api2}/branches/undo`, { params: { name: b.name } })
    if (!data.length) { toast.info(`Nothing to undo on ${b.name}`); return }
    setUndoFor({ branch: b.name, entries: data })
  })

  const doUndo = () => run(async () => {
    const { data } = await api.post(`${api2}/branches/undo`, { name: undoFor.branch })
    toast.success(`Undone: ${data.undone}`)
    setUndoFor(null)
    load()
  })

  const doMerge = (e) => {
    e.preventDefault()
    return run(async () => {
      const { data } = await api.post(`${api2}/merges`, { source: merge.source, target: merge.target })
      if (data.status === 'CONFLICTS') {
        toast.info('The merge has conflicts. Resolve them to finish.')
        navigate(`../merges/${data.session.sessionId}`, { relative: 'path' })
        return
      }
      const text = { UP_TO_DATE: `${merge.target} already contains ${merge.source}`, FAST_FORWARD: 'Merged (fast-forward)', MERGED: 'Merged' }[data.status]
      toast.success(text)
      load()
    })
  }

  if (error) return <p className="text-sm text-danger">{error.message}</p>
  if (!branches || !repo) return <p className="text-sm text-muted">Loading…</p>

  return (
    <div className="space-y-8">
      <section aria-label="Branches">
        <ul className="divide-y divide-line rounded-lg border border-line bg-white">
          {branches.map((b) => (
            <li key={b.name} className="flex flex-wrap items-center gap-3 p-3">
              <Link to={`..?ref=${encodeURIComponent(b.name)}`} relative="path" className="font-mono font-medium hover:underline">{b.name}</Link>
              {b.isDefault && <span className="rounded border border-line px-1.5 text-xs text-muted">default</span>}
              {b.isProtected && <span className="rounded border border-line px-1.5 text-xs text-muted">protected</span>}
              <Link to={`../commits/${b.head}`} relative="path" className="hash hover:underline">{shortHash(b.head)}</Link>
              {canWrite && (
                <span className="ml-auto flex gap-3 text-sm">
                  <button className="text-commit hover:underline" disabled={busy} onClick={() => askUndo(b)}>Undo</button>
                  {!b.isProtected && (confirmDelete === b.name ? (
                    <span className="flex gap-2">
                      <button className="text-danger hover:underline" disabled={busy} onClick={() => deleteBranch(b)}>Yes, delete</button>
                      <button className="text-muted hover:underline" onClick={() => setConfirmDelete(null)}>Cancel</button>
                    </span>
                  ) : (
                    <button className="text-danger hover:underline" onClick={() => setConfirmDelete(b.name)}>Delete</button>
                  ))}
                </span>
              )}
            </li>
          ))}
        </ul>
      </section>

      {undoFor && (
        <section className="rounded-lg border border-conflict/50 bg-white p-4" role="alertdialog" aria-labelledby="undo-title">
          <h3 id="undo-title" className="text-sm font-semibold">Undo the latest change on {undoFor.branch}?</h3>
          <p className="mt-1 text-xs text-muted">Undo steps back commits, restores and merges, one at a time. Recent changes on this branch:</p>
          <ul className="mt-2 list-inside list-disc text-sm text-muted">
            {undoFor.entries.slice(0, 5).map((e, i) => <li key={i}>{e}</li>)}
          </ul>
          <div className="mt-3 flex gap-2">
            <button className="btn-primary" disabled={busy} onClick={doUndo}>Undo</button>
            <button className="btn-secondary" onClick={() => setUndoFor(null)}>Cancel</button>
          </div>
        </section>
      )}

      {canWrite ? (
        <div className="grid gap-8 md:grid-cols-2">
          <form onSubmit={createBranch} className="space-y-3" aria-labelledby="new-branch">
            <h2 id="new-branch" className="text-base font-semibold">New branch</h2>
            <div>
              <label htmlFor="bname" className="mb-1 block text-sm font-medium">Name</label>
              <input id="bname" className="input font-mono" required maxLength={100} placeholder="feat/login" value={create.name}
                onChange={(e) => setCreate({ ...create, name: e.target.value })} />
            </div>
            <div>
              <label htmlFor="bfrom" className="mb-1 block text-sm font-medium">Start from</label>
              <select id="bfrom" className="input" value={create.from} onChange={(e) => setCreate({ ...create, from: e.target.value })}>
                {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
              </select>
            </div>
            <button className="btn-primary" disabled={busy || !create.name.trim()}>Create branch</button>
          </form>

          <form onSubmit={doMerge} className="space-y-3" aria-labelledby="merge-title">
            <h2 id="merge-title" className="text-base font-semibold">Merge</h2>
            <div>
              <label htmlFor="msrc" className="mb-1 block text-sm font-medium">Merge this branch…</label>
              <select id="msrc" className="input" value={merge.source} onChange={(e) => setMerge({ ...merge, source: e.target.value })}>
                {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="mtgt" className="mb-1 block text-sm font-medium">…into</label>
              <select id="mtgt" className="input" value={merge.target} onChange={(e) => setMerge({ ...merge, target: e.target.value })}>
                {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
              </select>
            </div>
            <button className="btn-primary" disabled={busy || !merge.source || merge.source === merge.target}>Merge</button>
            <p className="text-xs text-muted">If both branches changed the same file you'll resolve the conflicts next. For a reviewed merge, use a pull request.</p>
          </form>
        </div>
      ) : (
        <p className="text-sm text-muted">You need Write access to create, delete, undo or merge branches.</p>
      )}
    </div>
  )
}

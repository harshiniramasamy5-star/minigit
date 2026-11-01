import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { useToast } from '../context/ToastContext'
import { shortHash } from '../lib/useRepo'

const TONE = { ADDED: 'text-commit', MODIFIED: 'text-conflict', DELETED: 'text-danger' }

const decode = (buffer) => {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(buffer)
  } catch {
    return null   // not valid text: can't be edited in the browser
  }
}

// U3: your own server-side copy of the repo. Add or edit files, then commit.
export default function WorkingCopyPanel({ owner, name, branches, onCommitted }) {
  const toast = useToast()
  const base = `/repos/${owner}/${name}/worktree`
  const [wc, setWc] = useState(null)
  const [error, setError] = useState(null)
  const [editor, setEditor] = useState(null)         // { path, content, isNew }
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [commitError, setCommitError] = useState(null)
  const [moved, setMoved] = useState(false)          // BRANCH_HEAD_MOVED dialog
  const [newBranch, setNewBranch] = useState('')

  const load = useCallback(() => {
    api.get(base, { silent: true }).then((r) => { setWc(r.data); setError(null) }).catch(setError)
  }, [base])
  useEffect(load, [load])

  const save = async () => {
    if (!editor.path.trim()) return
    setBusy(true)
    try {
      const form = new FormData()
      form.append('path', editor.path.trim())
      form.append('file', new Blob([editor.content], { type: 'text/plain' }), 'file')
      await api.put(`${base}/files`, form)
      setEditor(null)
      load()
    } catch { /* toast shown, e.g. a bad path */ } finally { setBusy(false) }
  }

  const upload = async (file) => {
    if (!file) return
    const path = window.prompt('Save as (path in the repo):', file.name)
    if (!path) return
    try {
      const form = new FormData()
      form.append('path', path)
      form.append('file', file)
      await api.put(`${base}/files`, form)
      load()
    } catch { /* toast shown */ }
  }

  const edit = async (path) => {
    try {
      const { data } = await api.get(`${base}/file`, { params: { path }, responseType: 'arraybuffer' })
      const text = decode(data)
      if (text === null) { toast.info('That file is binary, so it can\'t be edited here. Upload a new version instead.'); return }
      setEditor({ path, content: text, isNew: false })
    } catch { /* toast shown */ }
  }

  const remove = async (path) => {
    try { await api.delete(`${base}/files`, { params: { path } }); load() } catch { /* toast shown */ }
  }

  const discard = async () => {
    if (!window.confirm('Throw away all uncommitted changes and move to the latest version of this branch?')) return
    try {
      await api.post(`${base}/discard`)
      setMoved(false)
      toast.success('Working copy reset')
      load()
    } catch { /* toast shown */ }
  }

  const switchBranch = async (branch) => {
    try { await api.post(`${base}/checkout`, { branch }); load() } catch { /* toast shown, e.g. uncommitted changes */ }
  }

  const commit = async (toNewBranch) => {
    setBusy(true)
    setCommitError(null)
    try {
      const { data } = await api.post(`/repos/${owner}/${name}/commits`,
        { message, newBranch: toNewBranch ? newBranch : undefined }, { silent: true })
      toast.success(`Committed ${shortHash(data.hash)}${toNewBranch ? ` on ${newBranch}` : ''}`)
      setMessage(''); setMoved(false); setNewBranch('')
      load()
      onCommitted?.()
    } catch (err) {
      if (err.code === 'BRANCH_HEAD_MOVED') setMoved(true)
      else setCommitError(err.message)
    } finally { setBusy(false) }
  }

  if (error) return <p className="text-sm text-danger">{error.message}</p>
  if (!wc) return <p className="text-sm text-muted">Loading your working copy…</p>

  return (
    <section className="rounded-lg border border-line bg-white p-4" aria-labelledby="wc">
      <div className="flex flex-wrap items-center gap-3">
        <h2 id="wc" className="text-base font-semibold">Your working copy</h2>
        <label htmlFor="wc-branch" className="sr-only">Branch</label>
        <select id="wc-branch" className="input w-52" value={wc.branch} onChange={(e) => switchBranch(e.target.value)}>
          {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
        </select>
        <span className="hash">based on {shortHash(wc.baseCommit)}</span>
        <button className="btn-secondary ml-auto" onClick={discard} disabled={!wc.changes.length && !wc.headMoved}>Discard changes</button>
      </div>

      {wc.headMoved && (
        <p className="mt-3 rounded border border-conflict/40 bg-white p-2 text-sm text-conflict" role="status">
          {wc.branch} has new commits since you started. Committing here will be refused; discard your changes or commit to a new branch.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button className="btn-secondary" onClick={() => setEditor({ path: '', content: '', isNew: true })}>New file</button>
        <label className="btn-secondary cursor-pointer">
          Upload file
          <input type="file" className="sr-only" onChange={(e) => { upload(e.target.files[0]); e.target.value = '' }} />
        </label>
      </div>

      {editor && (
        <div className="mt-3 space-y-2 rounded border border-line p-3">
          <label htmlFor="ed-path" className="block text-sm font-medium">Path</label>
          <input id="ed-path" className="input font-mono" placeholder="src/Main.java" value={editor.path}
            readOnly={!editor.isNew} onChange={(e) => setEditor({ ...editor, path: e.target.value })} />
          <label htmlFor="ed-content" className="block text-sm font-medium">Content</label>
          <textarea id="ed-content" className="input h-56 font-mono text-xs" spellCheck={false} value={editor.content}
            onChange={(e) => setEditor({ ...editor, content: e.target.value })} />
          <div className="flex gap-2">
            <button className="btn-primary" disabled={busy || !editor.path.trim()} onClick={save}>Save file</button>
            <button className="btn-secondary" onClick={() => setEditor(null)}>Cancel</button>
          </div>
        </div>
      )}

      <h3 className="mt-5 text-sm font-semibold">Files ({wc.files.length})</h3>
      {wc.files.length === 0 ? (
        <p className="mt-1 text-sm text-muted">Empty. Add a file to get started.</p>
      ) : (
        <ul className="mt-2 divide-y divide-line rounded border border-line text-sm">
          {wc.files.map((f) => {
            const change = wc.changes.find((c) => c.path === f.path)
            return (
              <li key={f.path} className="flex items-center gap-3 px-3 py-2">
                <span className="min-w-0 flex-1 truncate font-mono">{f.path}</span>
                {change && <span className={`text-xs font-medium ${TONE[change.status]}`}>{change.status}</span>}
                <span className="text-xs text-muted">{f.size} B</span>
                <button className="text-commit hover:underline" onClick={() => edit(f.path)}>Edit</button>
                <button className="text-danger hover:underline" onClick={() => remove(f.path)}>Delete</button>
              </li>
            )
          })}
        </ul>
      )}
      {wc.changes.filter((c) => c.status === 'DELETED').map((c) => (
        <p key={c.path} className="mt-1 text-xs text-danger">Deleted: <span className="font-mono">{c.path}</span></p>
      ))}

      <h3 className="mt-5 text-sm font-semibold">Commit {wc.changes.length ? `(${wc.changes.length} change${wc.changes.length === 1 ? '' : 's'})` : ''}</h3>
      <div className="mt-2 flex flex-wrap gap-2">
        <label htmlFor="msg" className="sr-only">Commit message</label>
        <input id="msg" className="input max-w-md flex-1" placeholder="Commit message" maxLength={1000} value={message}
          onChange={(e) => setMessage(e.target.value)} />
        <button className="btn-primary" disabled={busy || !message.trim() || !wc.changes.length} onClick={() => commit(false)}>
          Commit to {wc.branch}
        </button>
      </div>
      {!wc.changes.length && <p className="mt-1 text-xs text-muted">No changes yet.</p>}
      {commitError && <p className="mt-2 text-sm text-danger" role="alert">{commitError}</p>}

      {moved && (
        <div className="mt-3 space-y-3 rounded border border-conflict/50 p-3" role="alertdialog" aria-labelledby="moved-title">
          <p id="moved-title" className="text-sm font-medium">{wc.branch} moved since you started</p>
          <p className="text-sm text-muted">Someone committed to it. Your changes were not saved as a commit. Pick one:</p>
          <div className="flex flex-wrap items-center gap-2">
            <button className="btn-secondary" onClick={discard}>Discard my changes</button>
            <span className="text-sm text-muted">or</span>
            <label htmlFor="nb" className="sr-only">New branch name</label>
            <input id="nb" className="input w-52" placeholder="new-branch-name" value={newBranch}
              onChange={(e) => setNewBranch(e.target.value)} />
            <button className="btn-primary" disabled={busy || !newBranch.trim()} onClick={() => commit(true)}>Commit to new branch</button>
            <button className="text-sm text-muted hover:underline" onClick={() => setMoved(false)}>Close</button>
          </div>
        </div>
      )}
    </section>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useToast } from '../context/ToastContext'
import { useRepo } from '../lib/useRepo'

const KIND = {
  BOTH_MODIFIED: 'Both branches changed this file',
  BOTH_ADDED: 'Both branches added this file with different content',
  MODIFIED_DELETED: 'Changed on the target, deleted on the source',
  DELETED_MODIFIED: 'Deleted on the target, changed on the source',
}
const missing = (kind) => ({
  ours: kind === 'DELETED_MODIFIED',
  theirs: kind === 'MODIFIED_DELETED',
  base: kind === 'BOTH_ADDED',
})

function Side({ title, text, binary, gone }) {
  return (
    <div className="min-w-0">
      <h4 className="mb-1 text-xs font-semibold text-muted">{title}</h4>
      <pre className="h-40 overflow-auto rounded border border-line bg-paper p-2 font-mono text-xs leading-5">
        {binary ? 'Binary file' : gone ? "(file doesn't exist here)" : text}
      </pre>
    </div>
  )
}

// U7: resolve the conflicts of a merge session, then finish (creates the merge commit) or abort.
export default function ConflictPage() {
  const { owner, repo: name, sessionId } = useParams()
  const { repo } = useRepo(owner, name)
  const toast = useToast()
  const navigate = useNavigate()
  const [session, setSession] = useState(null)
  const [error, setError] = useState(null)
  const [editing, setEditing] = useState({})        // path -> text being edited
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const base = repo ? `/repos/${repo.id}/merges/${sessionId}` : null

  const load = useCallback(() => {
    if (!base) return
    api.get(base, { silent: true }).then((r) => setSession(r.data)).catch(setError)
  }, [base])
  useEffect(load, [load])

  useEffect(() => {
    if (session && !message) setMessage(`Merge ${session.sourceBranch} into ${session.targetBranch}`)
  }, [session])   // eslint-disable-line react-hooks/exhaustive-deps

  const run = async (fn) => {
    setBusy(true)
    try { await fn() } catch { /* toast shown */ } finally { setBusy(false) }
  }
  const resolve = (path, choice, content) => run(async () => {
    const { data } = await api.post(`${base}/resolve`, { path, choice, content })
    setSession(data)
    setEditing((e) => { const n = { ...e }; delete n[path]; return n })
  })
  const finish = () => run(async () => {
    await api.post(`${base}/finish`, { message })
    toast.success('Merge committed')
    navigate(`/${owner}/${name}/commits`)
  })
  const abort = () => run(async () => {
    await api.post(`${base}/abort`)
    toast.info('Merge aborted. Nothing was changed.')
    navigate(`/${owner}/${name}/branches`)
  })

  if (error) {
    return (
      <div className="rounded-lg border border-line bg-white p-6 text-sm">
        <p className="font-medium">This merge session isn't available.</p>
        <p className="mt-1 text-muted">{error.status === 404
          ? 'Sessions end when they are finished or aborted, after an hour, or when the server restarts. Start the merge again.'
          : error.message}</p>
      </div>
    )
  }
  if (!session) return <p className="text-sm text-muted">Loading…</p>
  const left = session.conflicts.length

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Resolve conflicts</h2>
        <p className="mt-1 text-sm text-muted">
          Merging <span className="font-mono">{session.sourceBranch}</span> into <span className="font-mono">{session.targetBranch}</span>.
          Nothing is committed until you finish.
        </p>
      </div>

      {session.conflicts.map((c) => {
        const gone = missing(c.kind)
        const isEditing = editing[c.path] !== undefined
        return (
          <section key={c.path} className="rounded-lg border border-line bg-white p-4" aria-label={`Conflict in ${c.path}`}>
            <div className="flex flex-wrap items-baseline gap-2">
              <span className="font-mono text-sm font-medium">{c.path}</span>
              <span className="text-xs text-conflict">{KIND[c.kind]}</span>
            </div>
            <div className="mt-3 grid gap-3 md:grid-cols-3">
              <Side title={`Ours (${session.targetBranch})`} text={c.ours} binary={c.binary} gone={gone.ours} />
              <Side title={`Theirs (${session.sourceBranch})`} text={c.theirs} binary={c.binary} gone={gone.theirs} />
              <Side title="Common ancestor" text={c.base} binary={c.binary} gone={gone.base} />
            </div>
            {isEditing && (
              <div className="mt-3 space-y-2">
                <label htmlFor={`edit-${c.path}`} className="block text-sm font-medium">Final content</label>
                <textarea id={`edit-${c.path}`} className="input h-40 font-mono text-xs" spellCheck={false}
                  value={editing[c.path]} onChange={(e) => setEditing({ ...editing, [c.path]: e.target.value })} />
                <div className="flex gap-2">
                  <button className="btn-primary" disabled={busy} onClick={() => resolve(c.path, 'custom', editing[c.path])}>Use this content</button>
                  <button className="btn-secondary" onClick={() => setEditing((e) => { const n = { ...e }; delete n[c.path]; return n })}>Cancel</button>
                </div>
              </div>
            )}
            {!isEditing && (
              <div className="mt-3 flex flex-wrap gap-2">
                <button className="btn-secondary" disabled={busy} onClick={() => resolve(c.path, 'ours')}>
                  {gone.ours ? 'Keep ours (deleted)' : 'Keep ours'}
                </button>
                <button className="btn-secondary" disabled={busy} onClick={() => resolve(c.path, 'theirs')}>
                  {gone.theirs ? 'Keep theirs (deleted)' : 'Keep theirs'}
                </button>
                {!c.binary && !(gone.ours && gone.theirs) && (
                  <button className="btn-secondary" onClick={() => setEditing({ ...editing, [c.path]: c.ours ?? c.theirs ?? '' })}>Edit by hand</button>
                )}
                <button className="btn-secondary text-danger" disabled={busy} onClick={() => resolve(c.path, 'delete')}>Delete the file</button>
              </div>
            )}
          </section>
        )
      })}

      <section className="rounded-lg border border-line bg-white p-4" aria-labelledby="finish">
        <h3 id="finish" className="text-sm font-semibold">
          {left ? `${left} conflict${left === 1 ? '' : 's'} left` : 'All conflicts resolved'}
        </h3>
        {!left && (
          <div className="mt-3 space-y-2">
            <label htmlFor="mmsg" className="block text-sm font-medium">Merge commit message</label>
            <input id="mmsg" className="input max-w-xl" value={message} onChange={(e) => setMessage(e.target.value)} />
          </div>
        )}
        <div className="mt-3 flex gap-2">
          <button className="btn-primary" disabled={busy || left > 0 || !message.trim()} onClick={finish}>Finish merge</button>
          <button className="btn-secondary" disabled={busy} onClick={abort}>Abort merge</button>
        </div>
      </section>
    </div>
  )
}

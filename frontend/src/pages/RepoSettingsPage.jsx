import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useToast } from '../context/ToastContext'

// U2 + U9: rename / describe / visibility, collaborators, delete (to the recycle bin). Owner only.
export default function RepoSettingsPage() {
  const { owner, repo: repoName } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const base = `/repos/${owner}/${repoName}`

  const [repo, setRepo] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    setRepo(null)
    api.get(base, { silent: true }).then((res) => setRepo(res.data)).catch(setError)
  }, [base])

  if (error) return <p className="text-sm text-danger">{error.message}</p>
  if (!repo) return <p className="text-sm text-muted">Loading…</p>
  if (repo.myRole !== 'OWNER') {
    return <p className="rounded-lg border border-line bg-surface p-6 text-sm text-muted">Only the owner can change settings or manage collaborators.</p>
  }

  return (
    <div className="max-w-2xl space-y-10">
      <General repo={repo} base={base} onSaved={(r) => {
        toast.success('Saved')
        if (r.name !== repoName) navigate(`/${r.owner}/${r.name}/settings`, { replace: true })
        else setRepo(r)
      }} />
      <Collaborators base={base} ownerName={repo.owner} />
      <DangerZone repo={repo} base={base} onDeleted={() => {
        toast.success(`${repo.name} moved to the recycle bin. You have 7 days to restore it.`)
        navigate('/recycle-bin')
      }} />
    </div>
  )
}

function General({ repo, base, onSaved }) {
  const [form, setForm] = useState({ name: repo.name, description: repo.description ?? '', visibility: repo.visibility })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.patch(base, form, { silent: true })
      onSaved(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-labelledby="general">
      <h2 id="general" className="text-lg font-semibold">General</h2>
      <form onSubmit={submit} className="mt-4 space-y-4">
        <div>
          <label htmlFor="rname" className="mb-1 block text-sm font-medium">Name</label>
          <input id="rname" className="input" required maxLength={100} pattern="[A-Za-z0-9._\-]+" value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div>
          <label htmlFor="rdesc" className="mb-1 block text-sm font-medium">Description</label>
          <input id="rdesc" className="input" maxLength={500} value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <div>
          <label htmlFor="rvis" className="mb-1 block text-sm font-medium">Visibility</label>
          <select id="rvis" className="input" value={form.visibility} onChange={(e) => setForm({ ...form, visibility: e.target.value })}>
            <option value="PRIVATE">Private: only you and collaborators</option>
            <option value="PUBLIC">Public: anyone can browse</option>
          </select>
        </div>
        {error && <p className="text-sm text-danger" role="alert">{error}</p>}
        <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button>
      </form>
    </section>
  )
}

function Collaborators({ base, ownerName }) {
  const toast = useToast()
  const [list, setList] = useState(null)
  const [username, setUsername] = useState('')
  const [role, setRole] = useState('READ')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api.get(`${base}/collaborators`).then((res) => setList(res.data)).catch(() => setList([]))
  }, [base])
  useEffect(load, [load])

  const setRoleFor = async (name, newRole) => {
    try {
      await api.put(`${base}/collaborators/${encodeURIComponent(name)}`, { role: newRole })
      load()
    } catch { /* toast shown */ }
  }

  const add = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      await api.put(`${base}/collaborators/${encodeURIComponent(username.trim())}`, { role })
      toast.success(`${username.trim()} can now ${role === 'WRITE' ? 'edit' : 'view'} this repository`)
      setUsername('')
      load()
    } catch { /* toast shown, e.g. no such user */ } finally {
      setBusy(false)
    }
  }

  const remove = async (name) => {
    try {
      await api.delete(`${base}/collaborators/${encodeURIComponent(name)}`)
      load()
    } catch { /* toast shown */ }
  }

  return (
    <section aria-labelledby="collab">
      <h2 id="collab" className="text-lg font-semibold">Collaborators</h2>
      <p className="mt-1 text-sm text-muted">Read can browse and comment. Write can also commit, branch, merge and open pull requests.</p>

      <ul className="mt-4 divide-y divide-line rounded-lg border border-line bg-surface">
        {(list ?? []).map((c) => (
          <li key={c.userId} className="flex items-center gap-3 p-3 text-sm">
            <span className="flex-1 font-medium">{c.username}</span>
            {c.username === ownerName ? (
              <span className="text-muted">Owner</span>
            ) : (
              <>
                <label className="sr-only" htmlFor={`role-${c.userId}`}>Role for {c.username}</label>
                <select id={`role-${c.userId}`} className="input w-28" value={c.role} onChange={(e) => setRoleFor(c.username, e.target.value)}>
                  <option value="READ">Read</option>
                  <option value="WRITE">Write</option>
                </select>
                <button className="btn-secondary text-danger" onClick={() => remove(c.username)}>Remove</button>
              </>
            )}
          </li>
        ))}
        {list && list.length <= 1 && <li className="p-3 text-sm text-muted">Only you so far.</li>}
      </ul>

      <form onSubmit={add} className="mt-4 flex flex-wrap gap-2">
        <label htmlFor="collab-user" className="sr-only">Username to add</label>
        <input id="collab-user" className="input max-w-xs" placeholder="username" required value={username}
          onChange={(e) => setUsername(e.target.value)} />
        <label htmlFor="collab-role" className="sr-only">Role</label>
        <select id="collab-role" className="input w-28" value={role} onChange={(e) => setRole(e.target.value)}>
          <option value="READ">Read</option>
          <option value="WRITE">Write</option>
        </select>
        <button className="btn-primary" disabled={busy}>Add</button>
      </form>
    </section>
  )
}

function DangerZone({ repo, base, onDeleted }) {
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)

  const remove = async () => {
    setBusy(true)
    try {
      await api.delete(base)
      onDeleted()
    } catch { /* toast shown */ } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-labelledby="danger" className="rounded-lg border border-danger/40 p-4">
      <h2 id="danger" className="text-lg font-semibold text-danger">Delete this repository</h2>
      <p className="mt-1 text-sm text-muted">It moves to the recycle bin for 7 days. You can restore it from there until then.</p>
      <label htmlFor="confirm-name" className="mt-3 block text-sm">Type <code className="font-mono">{repo.name}</code> to confirm</label>
      <div className="mt-1 flex gap-2">
        <input id="confirm-name" className="input max-w-xs" value={typed} onChange={(e) => setTyped(e.target.value)} />
        <button className="btn-danger" disabled={typed !== repo.name || busy} onClick={remove}>Delete</button>
      </div>
    </section>
  )
}

import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'

// U8: open a pull request (needs Write access).
export default function NewPullPage() {
  const { owner, repo: name } = useParams()
  const navigate = useNavigate()
  const [branches, setBranches] = useState([])
  const [form, setForm] = useState({ title: '', description: '', source: '', target: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.get(`/repos/${owner}/${name}/branches`, { silent: true }).then((r) => {
      setBranches(r.data)
      const def = r.data.find((b) => b.isDefault) ?? r.data[0]
      const other = r.data.find((b) => b.name !== def?.name)
      setForm((f) => ({ ...f, target: def?.name ?? '', source: other?.name ?? '' }))
    }).catch(() => {})
  }, [owner, name])

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      const { data } = await api.post(`/repos/${owner}/${name}/pulls`, form, { silent: true })
      navigate(`../${data.number}`, { relative: 'path' })
    } catch (err) {
      setError(err.message)
    } finally { setBusy(false) }
  }

  return (
    <div className="max-w-2xl">
      <h2 className="text-lg font-semibold">New pull request</h2>
      {branches.length < 2 ? (
        <p className="mt-4 rounded-lg border border-line bg-white p-4 text-sm text-muted">
          A pull request needs two branches. Commit to a new branch first (the Code tab offers this when you commit).
        </p>
      ) : (
        <form onSubmit={submit} className="mt-4 space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label htmlFor="target" className="mb-1 block text-sm font-medium">Merge into</label>
              <select id="target" className="input w-52" value={form.target} onChange={(e) => setForm({ ...form, target: e.target.value })}>
                {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
              </select>
            </div>
            <span className="pb-2 text-muted" aria-hidden>←</span>
            <div>
              <label htmlFor="source" className="mb-1 block text-sm font-medium">From</label>
              <select id="source" className="input w-52" value={form.source} onChange={(e) => setForm({ ...form, source: e.target.value })}>
                {branches.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
              </select>
            </div>
          </div>
          <div>
            <label htmlFor="title" className="mb-1 block text-sm font-medium">Title</label>
            <input id="title" className="input" required maxLength={200} value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div>
            <label htmlFor="desc" className="mb-1 block text-sm font-medium">Description <span className="font-normal text-muted">(optional)</span></label>
            <textarea id="desc" className="input h-32" maxLength={5000} value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          {error && <p className="text-sm text-danger" role="alert">{error}</p>}
          <button className="btn-primary" disabled={busy || form.source === form.target}>{busy ? 'Opening…' : 'Open pull request'}</button>
        </form>
      )}
    </div>
  )
}

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'

// U2: create a repository. The server adds main and an empty first commit.
export default function NewRepoPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', description: '', visibility: 'PRIVATE' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.post('/repos', form, { silent: true })
      navigate(`/${data.owner}/${data.name}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="text-2xl font-semibold tracking-tight">New repository</h1>
      <form onSubmit={submit} className="mt-6 space-y-4">
        <div>
          <label htmlFor="name" className="mb-1 block text-sm font-medium">Name</label>
          <input id="name" className="input" required maxLength={100} pattern="[A-Za-z0-9._\-]+"
            title="Letters, digits, '.', '_' or '-'" value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <p className="mt-1 text-xs text-muted">Letters, digits, <code className="font-mono">.</code> <code className="font-mono">_</code> <code className="font-mono">-</code> only.</p>
        </div>
        <div>
          <label htmlFor="description" className="mb-1 block text-sm font-medium">Description <span className="font-normal text-muted">(optional)</span></label>
          <input id="description" className="input" maxLength={500} value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <fieldset>
          <legend className="mb-1 text-sm font-medium">Visibility</legend>
          {[['PRIVATE', 'Private', 'Only you and the collaborators you add.'], ['PUBLIC', 'Public', 'Anyone can browse it, including guests.']].map(([value, label, hint]) => (
            <label key={value} className="mb-2 flex cursor-pointer items-start gap-2 text-sm">
              <input type="radio" name="visibility" className="mt-1" checked={form.visibility === value}
                onChange={() => setForm({ ...form, visibility: value })} />
              <span><span className="font-medium">{label}</span><br /><span className="text-muted">{hint}</span></span>
            </label>
          ))}
        </fieldset>
        {error && <p className="text-sm text-danger" role="alert">{error}</p>}
        <button className="btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create repository'}</button>
      </form>
    </div>
  )
}

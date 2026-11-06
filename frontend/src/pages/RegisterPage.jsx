import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'
import PasswordInput from '../components/PasswordInput'

// U1: create an account, then log straight in.
export default function RegisterPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '', confirm: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    if (form.password !== form.confirm) {
      setError('The two passwords do not match')
      return
    }
    setBusy(true)
    try {
      await api.post('/auth/register', { username: form.username, email: form.email, password: form.password }, { silent: true })
      await login(form.username, form.password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-[340px] pt-4">
      <h1 className="text-center text-2xl font-light tracking-tight">Create your MiniGit account</h1>
      <form onSubmit={submit} className="box mt-4 space-y-4 p-4">
        <div>
          <label htmlFor="username" className="mb-1 block text-sm font-medium">Username</label>
          <input id="username" className="input" autoComplete="username" required minLength={3} maxLength={40}
            pattern="[A-Za-z0-9_\-]+" title="Letters, digits, _ or -" value={form.username} onChange={set('username')} />
          <p className="mt-1 text-xs text-muted">3-40 characters: letters, digits, _ or -. It becomes part of your repo URLs.</p>
        </div>
        <div>
          <label htmlFor="email" className="mb-1 block text-sm font-medium">Email</label>
          <input id="email" type="email" className="input" autoComplete="email" required value={form.email} onChange={set('email')} />
        </div>
        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium">Password</label>
          <PasswordInput id="password" autoComplete="new-password" required minLength={8} maxLength={72}
            value={form.password} onChange={set('password')} />
          <p className="mt-1 text-xs text-muted">8-72 characters.</p>
        </div>
        <div>
          <label htmlFor="confirm" className="mb-1 block text-sm font-medium">Confirm password</label>
          <PasswordInput id="confirm" autoComplete="new-password" required value={form.confirm} onChange={set('confirm')} />
        </div>
        {error && <p className="rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>}
        <button className="btn-primary w-full justify-center" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
      </form>
      <p className="mt-4 rounded-md border border-line p-4 text-center text-sm">Already have an account? <Link to="/login" className="text-link hover:underline">Log in</Link></p>
    </div>
  )
}

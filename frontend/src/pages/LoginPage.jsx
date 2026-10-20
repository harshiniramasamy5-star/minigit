import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Minimal login so the shell is testable end to end. U1 (Shrejal) owns the final version:
// register page, field validation, ACCOUNT_LOCKED message with the unlock time.
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ usernameOrEmail: '', password: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(form.usernameOrEmail, form.password)
      navigate(location.state?.from ?? '/', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="text-2xl font-semibold tracking-tight">Log in to MiniGit</h1>
      <form onSubmit={submit} className="mt-6 space-y-4">
        <div>
          <label htmlFor="user" className="mb-1 block text-sm font-medium">Username or email</label>
          <input id="user" className="input" autoComplete="username" required value={form.usernameOrEmail}
            onChange={(e) => setForm({ ...form, usernameOrEmail: e.target.value })} />
        </div>
        <div>
          <label htmlFor="pw" className="mb-1 block text-sm font-medium">Password</label>
          <input id="pw" type="password" className="input" autoComplete="current-password" required value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </div>
        {error && <p className="text-sm text-danger" role="alert">{error}</p>}
        <button className="btn-primary w-full justify-center" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
      </form>
      <p className="mt-4 text-sm text-muted">No account? <Link to="/register" className="text-commit hover:underline">Create one</Link></p>
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
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
  const [devAuth, setDevAuth] = useState(false)

  // Shell only: the dev login box shows while the backend runs in open-dev mode.
  useEffect(() => {
    api.get('/health', { silent: true })
      .then(({ data }) => setDevAuth(Boolean(data.devAuth)))
      .catch(() => setDevAuth(false))
  }, [])

  const goBack = () => navigate(location.state?.from ?? '/', { replace: true })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(form.usernameOrEmail, form.password)
      goBack()
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

      {devAuth && <DevLogin onDone={goBack} />}
    </div>
  )
}

// TEMPORARY. Remove together with open-dev mode when U1 auth is merged.
function DevLogin({ onDone }) {
  const { devLogin } = useAuth()
  const [username, setUsername] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await devLogin(username)
      onDone()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="mt-8 rounded-lg border border-dashed border-conflict/50 bg-white p-4" aria-labelledby="dev-login">
      <h2 id="dev-login" className="text-sm font-semibold">Dev mode</h2>
      <p className="mt-1 text-xs text-muted">
        Real login isn't merged yet. Continue as an existing user without a password.
        This box disappears when the backend's open-dev mode is turned off.
      </p>
      <form onSubmit={submit} className="mt-3 flex gap-2">
        <label htmlFor="dev-user" className="sr-only">Username</label>
        <input id="dev-user" className="input" placeholder="username" required value={username}
          onChange={(e) => setUsername(e.target.value)} />
        <button className="btn-secondary shrink-0" disabled={busy}>{busy ? 'Checking…' : 'Continue'}</button>
      </form>
      {error && <p className="mt-2 text-xs text-danger" role="alert">{error}</p>}
    </section>
  )
}

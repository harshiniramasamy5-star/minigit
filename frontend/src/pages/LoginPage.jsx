import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'
import PasswordInput from '../components/PasswordInput'

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
    <div className="mx-auto max-w-[340px] pt-4">
      <div className="flex flex-col items-center">
        <svg width="48" height="48" viewBox="0 0 22 22" fill="none" aria-hidden>
          <path d="M6 3v16" stroke="#3fb950" strokeWidth="2" strokeLinecap="round" />
          <path d="M6 15c0-5 10-3 10-8" stroke="#a371f7" strokeWidth="2" strokeLinecap="round" />
          <circle cx="6" cy="5" r="2.5" className="fill-paper" stroke="#3fb950" strokeWidth="2" />
          <circle cx="16" cy="6" r="2.5" className="fill-paper" stroke="#a371f7" strokeWidth="2" />
          <circle cx="6" cy="17" r="2.5" fill="#3fb950" />
        </svg>
        <h1 className="mt-4 text-2xl font-light tracking-tight">Sign in to MiniGit</h1>
      </div>

      <form onSubmit={submit} className="box mt-4 space-y-4 p-4">
        <div>
          <label htmlFor="user" className="mb-1 block text-sm font-medium">Username or email address</label>
          <input id="user" className="input" autoComplete="username" required value={form.usernameOrEmail}
            onChange={(e) => setForm({ ...form, usernameOrEmail: e.target.value })} />
        </div>
        <div>
          <label htmlFor="pw" className="mb-1 block text-sm font-medium">Password</label>
          {/* PasswordInput = the normal password field plus a show/hide eye button and a Caps Lock hint */}
          <PasswordInput id="pw" autoComplete="current-password" required value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </div>
        {error && <p className="rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>}
        <button className="btn-primary w-full justify-center" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <p className="mt-4 rounded-md border border-line p-4 text-center text-sm">
        New to MiniGit? <Link to="/register" className="text-link hover:underline">Create an account</Link>
      </p>

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
    <section className="mt-8 rounded-lg border border-dashed border-conflict/50 bg-surface p-4" aria-labelledby="dev-login">
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

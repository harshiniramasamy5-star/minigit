import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../lib/theme'
import Avatar from './Avatar'

const navClass = ({ isActive }) =>
  `rounded-md px-2.5 py-1.5 text-sm font-medium ${isActive ? 'bg-line/60 text-ink' : 'text-ink/80 hover:text-ink'}`

export default function Layout() {
  const { user, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()
  const [q, setQ] = useState('')

  // The search box reuses the Explore page (GET /explore?q=).
  const search = (e) => {
    e.preventDefault()
    navigate(q.trim() ? `/explore?q=${encodeURIComponent(q.trim())}` : '/explore')
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-line bg-header">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2 text-ink" aria-label="MiniGit home">
            <BranchMark />
            <span className="hidden text-[17px] font-semibold tracking-tight sm:inline">MiniGit</span>
          </Link>
          <nav className="flex gap-1">
            {user && <NavLink to="/" end className={navClass}>Dashboard</NavLink>}
            <NavLink to="/explore" className={navClass}>Explore</NavLink>
          </nav>

          <form onSubmit={search} role="search" className="ml-2 hidden flex-1 sm:block sm:max-w-xs">
            <label htmlFor="global-search" className="sr-only">Search repositories</label>
            <div className="relative">
              <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-muted" />
              <input id="global-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search repositories"
                className="input !bg-paper pl-8" />
            </div>
          </form>

          <div className="ml-auto flex items-center gap-2 text-sm">
            <button onClick={toggle} className="rounded-md border border-line p-1.5 text-muted hover:text-ink"
              aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
              title={theme === 'dark' ? 'Light theme' : 'Dark theme'}>
              {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
            </button>
            {user ? (
              <>
                <Link to="/new" className="rounded-md border border-line px-2.5 py-1.5 text-ink/90 hover:bg-line/40" title="New repository">
                  <span aria-hidden>+</span> <span className="hidden sm:inline">New</span>
                </Link>
                <Link to="/recycle-bin" className="hidden rounded-md px-2 py-1.5 text-muted hover:text-ink md:block">Recycle bin</Link>
                <span className="flex items-center gap-2 pl-1">
                  <Avatar name={user.username} size={28} />
                  <span className="hidden font-medium text-ink sm:inline">{user.username}</span>
                </span>
                {user.dev && (
                  <span className="rounded-full border border-conflict/60 px-2 text-xs text-conflict" title="Temporary dev login, no password">dev</span>
                )}
                <button onClick={logout} className="rounded-md px-2 py-1.5 text-muted hover:text-ink">Log out</button>
              </>
            ) : (
              <>
                <Link to="/login" className="rounded-md border border-line px-3 py-1.5 text-ink hover:bg-line/40">Sign in</Link>
                <Link to="/register" className="hidden rounded-md border border-white/10 bg-commit px-3 py-1.5 font-medium text-white hover:bg-commit-dark sm:block">Sign up</Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6">
        <Outlet />
      </main>

      <footer className="border-t border-line py-6 text-center text-xs text-muted">
        MiniGit · a Git-inspired version control app · CS1207 OOP Lab, NIT Warangal
      </footer>
    </div>
  )
}

// Two commits on main, one side branch merging back in.
function BranchMark() {
  return (
    <svg width="26" height="26" viewBox="0 0 22 22" fill="none" aria-hidden>
      <path d="M6 3v16" stroke="#3fb950" strokeWidth="2" strokeLinecap="round" />
      <path d="M6 15c0-5 10-3 10-8" stroke="#a371f7" strokeWidth="2" strokeLinecap="round" />
      <circle cx="6" cy="5" r="2.5" className="fill-header" stroke="#3fb950" strokeWidth="2" />
      <circle cx="16" cy="6" r="2.5" className="fill-header" stroke="#a371f7" strokeWidth="2" />
      <circle cx="6" cy="17" r="2.5" fill="#3fb950" />
    </svg>
  )
}

const icon = { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }

function SearchIcon({ className = '' }) {
  return <svg {...icon} className={className}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
}
function SunIcon() {
  return <svg {...icon}><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
}
function MoonIcon() {
  return <svg {...icon}><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" /></svg>
}

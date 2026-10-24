import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const navClass = ({ isActive }) =>
  `rounded px-2 py-1 text-sm ${isActive ? 'text-white' : 'text-white/70 hover:text-white'}`

export default function Layout() {
  const { user, logout } = useAuth()
  return (
    <div className="flex min-h-screen flex-col">
      <header className="bg-ink">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
          <Link to="/" className="flex items-center gap-2 text-white">
            <BranchMark />
            <span className="text-[17px] font-semibold tracking-tight">MiniGit</span>
          </Link>
          <nav className="flex gap-1">
            {user && <NavLink to="/" end className={navClass}>Dashboard</NavLink>}
            <NavLink to="/explore" className={navClass}>Explore</NavLink>
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm">
            {user ? (
              <>
                <span className="text-white/80">{user.username}</span>
                <button onClick={logout} className="text-white/70 hover:text-white">Log out</button>
              </>
            ) : (
              <Link to="/login" className="rounded-md bg-white/10 px-3 py-1.5 text-white hover:bg-white/20">Log in</Link>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}

// Two commits on main, one side branch merging back in.
function BranchMark() {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden>
      <path d="M6 3v16" stroke="#7FC4CB" strokeWidth="2" strokeLinecap="round" />
      <path d="M6 15c0-5 10-3 10-8" stroke="#B9A6E0" strokeWidth="2" strokeLinecap="round" />
      <circle cx="6" cy="5" r="2.5" fill="#1A2433" stroke="#7FC4CB" strokeWidth="2" />
      <circle cx="16" cy="6" r="2.5" fill="#1A2433" stroke="#B9A6E0" strokeWidth="2" />
      <circle cx="6" cy="17" r="2.5" fill="#7FC4CB" />
    </svg>
  )
}

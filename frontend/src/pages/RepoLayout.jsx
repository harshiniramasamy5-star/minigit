import { NavLink, Outlet, useParams } from 'react-router-dom'

const TABS = [
  { to: '', label: 'Code', end: true },
  { to: 'commits', label: 'Commits' },
  { to: 'graph', label: 'Graph' },
  { to: 'branches', label: 'Branches' },
  { to: 'pulls', label: 'Pull requests' },
  { to: 'settings', label: 'Settings' },
]

// Shared frame for every repo page. Child routes are in App.jsx.
export default function RepoLayout() {
  const { owner, repo } = useParams()
  return (
    <div>
      <h1 className="text-xl">
        <span className="text-muted">{owner} /</span> <span className="font-semibold">{repo}</span>
      </h1>
      <nav className="mt-4 flex gap-1 overflow-x-auto border-b border-line">
        {TABS.map((t) => (
          <NavLink key={t.label} to={t.to} end={t.end}
            className={({ isActive }) => `-mb-px whitespace-nowrap border-b-2 px-3 py-2 text-sm ${isActive ? 'border-commit font-medium text-ink' : 'border-transparent text-muted hover:text-ink'}`}>
            {t.label}
          </NavLink>
        ))}
      </nav>
      <div className="mt-6"><Outlet /></div>
    </div>
  )
}

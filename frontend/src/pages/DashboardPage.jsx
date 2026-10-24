import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'
import RepoCard from '../components/RepoCard'

export default function DashboardPage() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.get('/dashboard', { silent: true })
      .then((res) => setData(res.data))
      .catch(setError)
  }, [])

  return (
    <div className="grid gap-8 md:grid-cols-[1fr_18rem]">
      <section>
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Your repositories</h1>
          <Link to="/new" className="btn-primary">New repository</Link>
        </div>

        {error && <p className="mt-6 text-sm text-danger">{error.message}</p>}
        {!data && !error && <p className="mt-6 text-sm text-muted">Loading…</p>}

        {data && data.repos.length === 0 && (
          <div className="mt-6 rounded-lg border border-line bg-white p-8">
            <p className="font-medium">No repositories yet, {user.username}.</p>
            <p className="mt-1 text-sm text-muted">Create one to start committing files. It starts with a <code className="font-mono">main</code> branch and an empty first commit.</p>
          </div>
        )}
        {data && data.repos.length > 0 && (
          <ul className="mt-4 divide-y divide-line">
            {data.repos.map((r) => <RepoCard key={r.id} repo={r} />)}
          </ul>
        )}
      </section>

      <aside className="space-y-6">
        <div>
          <h2 className="text-sm font-semibold">Open pull requests</h2>
          {data?.openPulls?.length ? (
            <ul className="mt-2 space-y-2 text-sm">
              {data.openPulls.map((pr) => (
                <li key={`${pr.repo}#${pr.number}`}>
                  <Link to={`/${pr.repo}/pulls/${pr.number}`} className="hover:underline">{pr.title}</Link>
                  <span className="ml-1 text-muted">#{pr.number}</span>
                  <span className="block text-xs text-muted">{pr.repo} · {pr.source} → {pr.target}</span>
                </li>
              ))}
            </ul>
          ) : <p className="mt-2 text-sm text-muted">Nothing waiting on you.</p>}
        </div>
        <div>
          <h2 className="text-sm font-semibold">Recent commits</h2>
          {data?.recentCommits?.length ? (
            <ul className="mt-2 space-y-2 text-sm">
              {data.recentCommits.map((c) => (
                <li key={`${c.repo}:${c.hash}`}>
                  <Link to={`/${c.repo}/commits/${c.hash}`} className="hash hover:underline">{c.hash.slice(0, 7)}</Link> {c.message}
                  <span className="block text-xs text-muted">{c.repo} · {c.author}</span>
                </li>
              ))}
            </ul>
          ) : <p className="mt-2 text-sm text-muted">No commits yet.</p>}
        </div>
      </aside>
    </div>
  )
}

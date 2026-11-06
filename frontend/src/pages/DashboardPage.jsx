import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'
import ActivityOverview from '../components/ActivityOverview'
import Avatar from '../components/Avatar'
import ContributionGraph from '../components/ContributionGraph'
import RepoCard from '../components/RepoCard'
import { demoActivity } from '../lib/demoActivity'

// GitHub-style home: profile card on the left; contribution heatmap, activity overview and repositories on the right.
// Add ?demo=1 to the URL to preview the heatmap with sample data (no database needed).
export default function DashboardPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const demo = params.get('demo') === '1'

  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const [year, setYear] = useState(null)               // null = last 365 days
  const [activity, setActivity] = useState(null)
  const [activityLoading, setActivityLoading] = useState(true)
  const [activityError, setActivityError] = useState(null)

  useEffect(() => {
    api.get('/dashboard', { silent: true })
      .then((res) => setData(res.data))
      .catch(setError)
  }, [])

  // GET /activity?year=  (heatmap + activity overview). A failure here never blocks the repo list.
  useEffect(() => {
    let alive = true
    setActivityLoading(true)
    setActivityError(null)
    if (demo) {
      setActivity(demoActivity(year))
      setActivityLoading(false)
      return undefined
    }
    api.get('/activity', { params: year == null ? {} : { year }, silent: true })
      .then((res) => { if (alive) setActivity(res.data) })
      .catch((err) => { if (alive) setActivityError(err) })
      .finally(() => { if (alive) setActivityLoading(false) })
    return () => { alive = false }
  }, [year, demo])

  return (
    <div className="grid gap-8 md:grid-cols-[17rem_minmax(0,1fr)]">
      <aside className="space-y-6">
        <div className="flex items-center gap-4 md:block">
          <Avatar name={user.username} size={96} className="md:!h-60 md:!w-60 md:!text-8xl" />
          <div className="md:mt-4">
            <h1 className="text-2xl font-semibold leading-tight">{user.username}</h1>
            {data && (
              <p className="mt-1 text-sm text-muted">
                {data.repos.length} {data.repos.length === 1 ? 'repository' : 'repositories'}
              </p>
            )}
          </div>
        </div>

        <section className="box">
          <h2 className="box-header">Open pull requests</h2>
          {data?.openPulls?.length ? (
            <ul className="divide-y divide-line text-sm">
              {data.openPulls.map((pr) => (
                <li key={`${pr.repo}#${pr.number}`} className="px-4 py-2.5">
                  <Link to={`/${pr.repo}/pulls/${pr.number}`} className="font-medium hover:text-link hover:underline">{pr.title}</Link>
                  <span className="ml-1 text-muted">#{pr.number}</span>
                  <span className="block text-xs text-muted">{pr.repo} · {pr.source} → {pr.target}</span>
                </li>
              ))}
            </ul>
          ) : <p className="px-4 py-3 text-sm text-muted">Nothing waiting on you.</p>}
        </section>

        <section className="box">
          <h2 className="box-header">Recent commits</h2>
          {data?.recentCommits?.length ? (
            <ul className="divide-y divide-line text-sm">
              {data.recentCommits.map((c) => (
                <li key={`${c.repo}:${c.hash}`} className="px-4 py-2.5">
                  <span className="break-words">{c.message}</span>
                  <span className="mt-0.5 block text-xs text-muted">
                    <Link to={`/${c.repo}/commits/${c.hash}`} className="hash hover:text-link hover:underline">{c.hash.slice(0, 7)}</Link>
                    {' · '}{c.repo} · {c.author}
                  </span>
                </li>
              ))}
            </ul>
          ) : <p className="px-4 py-3 text-sm text-muted">No commits yet.</p>}
        </section>
      </aside>

      <div className="min-w-0">
        {demo && <p className="mb-3 text-xs text-muted">Showing sample activity (demo mode).</p>}
        <ContributionGraph activity={activity} year={year} onYearChange={setYear} loading={activityLoading} />
        {activityError && !activity && (
          <p className="mt-2 text-sm text-muted">Couldn't load contribution activity: {activityError.message}</p>
        )}
        <ActivityOverview activity={activity} />

        <section className="mt-8">
          <div className="flex items-center justify-between border-b border-line pb-3">
            <h2 className="text-xl font-semibold tracking-tight">Your repositories</h2>
            <Link to="/new" className="btn-primary">New repository</Link>
          </div>

          {error && <p className="mt-6 text-sm text-danger">{error.message}</p>}
          {!data && !error && <p className="mt-6 text-sm text-muted">Loading…</p>}

          {data && data.repos.length === 0 && (
            <div className="box mt-6 p-8">
              <p className="font-medium">No repositories yet, {user.username}.</p>
              <p className="mt-1 text-sm text-muted">Create one to start committing files. It starts with a <code className="font-mono">main</code> branch and an empty first commit.</p>
            </div>
          )}
          {data && data.repos.length > 0 && (
            <ul className="divide-y divide-line">
              {data.repos.map((r) => <RepoCard key={r.id} repo={r} />)}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

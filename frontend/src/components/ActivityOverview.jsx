import { Link } from 'react-router-dom'
import { breakdownPercents } from '../lib/contributions'
import { RepoIcon } from './RepoCard'

// "Activity overview": the repos you contributed to + a radar chart of what kind of work it was.
export default function ActivityOverview({ activity }) {
  if (!activity) return null
  const { repos, breakdown } = activity
  const shown = repos.slice(0, 3)
  const more = repos.length - shown.length
  const empty = activity.total === 0

  return (
    <section aria-labelledby="overview" className="mt-6 border-t border-line pt-4">
      <h2 id="overview" className="text-base font-normal">Activity overview</h2>
      {empty ? (
        <p className="mt-3 text-sm text-muted">No contributions in this period yet. Make a commit, open a pull request or review one and it will show up here.</p>
      ) : (
        <div className="mt-3 grid gap-6 md:grid-cols-2">
          <div className="flex gap-3 text-sm">
            <RepoIcon className="mt-1 shrink-0 text-muted" />
            <p className="leading-6">
              Contributed to{' '}
              {shown.map((r, i) => (
                <span key={r.repo}>
                  <Link to={`/${r.repo}`} className="font-semibold text-link hover:underline">{r.repo}</Link>
                  {i < shown.length - 1 ? ', ' : ''}
                </span>
              ))}
              {more > 0 && <> and {more} other {more === 1 ? 'repository' : 'repositories'}</>}
            </p>
          </div>
          <Radar breakdown={breakdown} />
        </div>
      )}
    </section>
  )
}

// Three axes (we track commits, pull requests and reviews). Each spoke is that share of all contributions.
const AXES = [
  { key: 'reviews', label: 'Code review', angle: -90 },
  { key: 'pullRequests', label: 'Pull requests', angle: 30 },
  { key: 'commits', label: 'Commits', angle: 150 },
]

function Radar({ breakdown }) {
  const pct = breakdownPercents(breakdown)
  const cx = 150, cy = 105, R = 62
  const point = (angle, r) => [cx + r * Math.cos((angle * Math.PI) / 180), cy + r * Math.sin((angle * Math.PI) / 180)]
  const max = Math.max(...AXES.map((a) => pct[a.key]), 1)
  const shape = AXES.map((a) => point(a.angle, Math.max(3, (pct[a.key] / max) * R)))
  const summary = AXES.map((a) => `${a.label} ${pct[a.key]}%`).join(', ')

  return (
    <svg viewBox="0 0 300 220" className="mx-auto w-full max-w-xs" role="img" aria-label={`Contribution mix: ${summary}`}>
      {[0.5, 1].map((k) => (
        <polygon key={k} points={AXES.map((a) => point(a.angle, R * k).join(',')).join(' ')} fill="none" stroke="rgb(var(--c-line))" strokeWidth="1" />
      ))}
      {AXES.map((a) => {
        const [x, y] = point(a.angle, R)
        return <line key={a.key} x1={cx} y1={cy} x2={x} y2={y} stroke="#3fb950" strokeWidth="2" />
      })}
      <polygon points={shape.map((p) => p.join(',')).join(' ')} fill="#3fb950" fillOpacity="0.35" stroke="#3fb950" strokeWidth="2" strokeLinejoin="round" />
      {shape.map(([x, y], i) => <circle key={i} cx={x} cy={y} r="3.5" style={{ fill: 'rgb(var(--c-paper))' }} stroke="#3fb950" strokeWidth="2" />)}
      {AXES.map((a) => {
        const [x, y] = point(a.angle, R + 16)
        const anchor = Math.abs(Math.cos((a.angle * Math.PI) / 180)) < 0.2 ? 'middle' : Math.cos((a.angle * Math.PI) / 180) > 0 ? 'start' : 'end'
        const dy = a.angle === -90 ? -14 : 4
        return (
          <text key={a.key} x={x} y={y + dy} textAnchor={anchor} fontSize="12" className="fill-muted">
            <tspan x={x} dy="0">{pct[a.key]}%</tspan>
            <tspan x={x} dy="14">{a.label}</tspan>
          </text>
        )
      })}
    </svg>
  )
}

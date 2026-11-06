import { useEffect, useMemo, useRef, useState } from 'react'
import { buildCalendar, fmtDay, statsOf } from '../lib/contributions'

const CELL = 11
const GAP = 3
const STEP = CELL + GAP
const LEFT = 30          // room for the Mon / Wed / Fri labels
const TOP = 18           // room for the month labels
const ROW_LABELS = { 1: 'Mon', 3: 'Wed', 5: 'Fri' }

// GitHub-style contribution heatmap with a year picker on the right.
//   activity: GET /activity response (or null while loading)
//   year: null = last 365 days, otherwise a calendar year
export default function ContributionGraph({ activity, year, onYearChange, loading = false }) {
  const scroller = useRef(null)
  const [tip, setTip] = useState(null)

  const calendar = useMemo(() => (activity ? buildCalendar(activity) : null), [activity])
  const stats = useMemo(() => (activity ? statsOf(activity) : null), [activity])

  // Newest weeks are on the right: start scrolled to the end, like GitHub.
  useEffect(() => {
    if (scroller.current) scroller.current.scrollLeft = scroller.current.scrollWidth
  }, [calendar])

  if (!activity || !calendar) {
    return <div className="box p-4 text-sm text-muted">{loading ? 'Loading contributions…' : 'Contribution activity is not available right now.'}</div>
  }

  const width = LEFT + calendar.weeks.length * STEP + 22      // extra room so the last month label is not clipped
  const height = TOP + 7 * STEP
  const title = year == null
    ? `${activity.total.toLocaleString()} contribution${activity.total === 1 ? '' : 's'} in the last year`
    : `${activity.total.toLocaleString()} contribution${activity.total === 1 ? '' : 's'} in ${year}`

  const showTip = (e, cell) => {
    const host = e.currentTarget.ownerSVGElement.parentElement.getBoundingClientRect()
    const r = e.currentTarget.getBoundingClientRect()
    setTip({
      x: r.left - host.left + r.width / 2, y: r.top - host.top,
      text: `${cell.count === 0 ? 'No contributions' : `${cell.count} contribution${cell.count === 1 ? '' : 's'}`} on ${fmtDay(cell.date)}`,
    })
  }

  return (
    <div className="grid gap-4 md:grid-cols-[1fr_7rem]">
      <div className={`min-w-0 ${loading ? 'opacity-60' : ''}`}>
        <h2 className="mb-2 text-base font-normal">{title}</h2>
        <div className="box p-3">
          <div className="relative">
            <div ref={scroller} className="overflow-x-auto pb-1">
              {/* viewBox keeps the whole year inside the card; below 640px wide it scrolls instead of shrinking further */}
              <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title} className="block w-full min-w-[640px]">
                {calendar.months.map((m) => (
                  <text key={m.col} x={LEFT + m.col * STEP} y={10} fontSize="12" className="fill-muted">{m.label}</text>
                ))}
                {Object.entries(ROW_LABELS).map(([row, label]) => (
                  <text key={label} x={0} y={TOP + Number(row) * STEP + CELL - 1} fontSize="12" className="fill-muted">{label}</text>
                ))}
                {calendar.weeks.map((week, c) => week.map((cell, r) => cell && (
                  <rect key={cell.date} x={LEFT + c * STEP} y={TOP + r * STEP} width={CELL} height={CELL} rx="2"
                    style={{ fill: `var(--heat-${cell.level})`, outline: '1px solid rgb(255 255 255 / 0.05)', outlineOffset: '-1px' }}
                    onMouseEnter={(e) => showTip(e, cell)} onMouseLeave={() => setTip(null)}>
                    <title>{`${cell.count} on ${fmtDay(cell.date)}`}</title>
                  </rect>
                )))}
              </svg>
            </div>
            {tip && (
              <div className="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-md bg-ink px-2 py-1 text-xs font-medium text-paper shadow-pop"
                style={{ left: tip.x, top: tip.y - 6 }} role="tooltip">
                {tip.text}
              </div>
            )}
          </div>

          <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-muted">
            <span>Commits, pull requests and reviews count as contributions.</span>
            <span className="flex items-center gap-1" aria-label="Colour legend">
              Less
              {[0, 1, 2, 3, 4].map((l) => (
                <span key={l} className="inline-block rounded-sm" style={{ width: CELL, height: CELL, background: `var(--heat-${l})` }} />
              ))}
              More
            </span>
          </div>
        </div>

        {stats && (
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            <Stat label="Active days" value={stats.activeDays} />
            <Stat label="Longest streak" value={`${stats.longestStreak} day${stats.longestStreak === 1 ? '' : 's'}`} />
            <Stat label="Current streak" value={`${stats.currentStreak} day${stats.currentStreak === 1 ? '' : 's'}`} />
            <Stat label="Busiest day" value={stats.busiestDay ? `${fmtDay(stats.busiestDay)} (${stats.busiestCount})` : '–'} />
          </dl>
        )}
      </div>

      <YearPicker years={activity.years} year={year} onChange={onYearChange} />
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div className="rounded-md border border-line px-3 py-2">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 font-semibold">{value}</dd>
    </div>
  )
}

function YearPicker({ years, year, onChange }) {
  const items = [{ key: 'last', label: 'Last year', value: null }, ...years.map((y) => ({ key: y, label: String(y), value: y }))]
  return (
    <nav aria-label="Contribution year" className="flex gap-2 overflow-x-auto md:flex-col md:overflow-visible md:pt-8">
      {items.map((it) => {
        const active = it.value === year
        return (
          <button key={it.key} onClick={() => onChange(it.value)} aria-current={active ? 'true' : undefined}
            className={`shrink-0 rounded-md px-4 py-2 text-left text-sm ${active ? 'bg-accent font-medium text-white' : 'text-muted hover:bg-line/40 hover:text-ink'}`}>
            {it.label}
          </button>
        )
      })}
    </nav>
  )
}

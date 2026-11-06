// Pure helpers for the contribution heatmap (no React, so they are easy to test: see contributions.check.mjs).
//
// Input is the GET /activity response:
//   { from, to, total, years, days: [{date, count, level?}], breakdown: {commits, pullRequests, reviews},
//     repos: [{repo, count}], stats?: {activeDays, longestStreak, currentStreak, busiestDay, busiestCount} }
// `level` and `stats` come from the Java ContributionLevel / ContributionCalendar classes. The functions
// here mirror those rules so an older backend (without the two fields) still renders correctly.

const DAY_MS = 86_400_000

const parse = (iso) => {
  const [y, m, d] = iso.split('-').map(Number)
  return Date.UTC(y, m - 1, d)
}
const iso = (ms) => new Date(ms).toISOString().slice(0, 10)

/** Same rule as ContributionLevel.of in Java: shade relative to the busiest day. */
export function levelFor(count, busiest) {
  if (count <= 0 || busiest <= 0) return 0
  const ratio = Math.min(1, count / busiest)
  if (ratio <= 0.25) return 1
  if (ratio <= 0.5) return 2
  if (ratio <= 0.75) return 3
  return 4
}

/**
 * Lay the period out like GitHub: one column per week (Sunday on top), 7 rows.
 * Returns { weeks: [[cell|null x7]], months: [{label, col}], busiest }
 * where cell = { date, count, level }. Cells before `from` / after `to` are null.
 */
export function buildCalendar(activity) {
  const fromMs = parse(activity.from)
  const toMs = parse(activity.to)
  const byDay = new Map(activity.days.map((d) => [d.date, d]))
  const busiest = activity.days.reduce((m, d) => Math.max(m, d.count), 0)

  const startMs = fromMs - new Date(fromMs).getUTCDay() * DAY_MS       // back up to that week's Sunday
  const weeks = []
  for (let weekStart = startMs; weekStart <= toMs; weekStart += 7 * DAY_MS) {
    const week = []
    for (let i = 0; i < 7; i++) {
      const ms = weekStart + i * DAY_MS
      if (ms < fromMs || ms > toMs) { week.push(null); continue }
      const date = iso(ms)
      const hit = byDay.get(date)
      const count = hit?.count ?? 0
      week.push({ date, count, level: hit?.level ?? levelFor(count, busiest) })
    }
    weeks.push(week)
  }

  // Month label above the first column that holds the 1st of that month (or the first column).
  const months = []
  let lastCol = -10
  weeks.forEach((week, col) => {
    const first = week.find((c) => c)
    if (!first) return
    const startsMonth = col === 0 || week.some((c) => c && c.date.endsWith('-01'))
    if (!startsMonth || col - lastCol < 3) return                       // keep labels from overlapping
    const date = week.find((c) => c && c.date.endsWith('-01'))?.date ?? first.date
    months.push({ label: new Date(parse(date)).toLocaleString('en-US', { month: 'short', timeZone: 'UTC' }), col })
    lastCol = col
  })

  return { weeks, months, busiest }
}

/** Same rules as ContributionCalendar.stats in Java. `today` is an ISO date. */
export function computeStats(activity, today) {
  const counts = new Map(activity.days.filter((d) => d.count > 0).map((d) => [d.date, d.count]))
  const dates = [...counts.keys()].sort()
  let longest = 0
  let run = 0
  let prev = null
  for (const d of dates) {
    run = prev !== null && parse(d) - prev === DAY_MS ? run + 1 : 1
    longest = Math.max(longest, run)
    prev = parse(d)
  }

  const fromMs = parse(activity.from)
  let end = Math.min(parse(today), parse(activity.to))
  if (end < fromMs) end = fromMs
  let cursor = counts.has(iso(end)) ? end : end - DAY_MS
  let current = 0
  while (cursor >= fromMs && counts.has(iso(cursor))) { current++; cursor -= DAY_MS }

  const busiestCount = dates.reduce((m, d) => Math.max(m, counts.get(d)), 0)
  const busiestDay = dates.find((d) => counts.get(d) === busiestCount) ?? null     // earliest wins a tie
  return { activeDays: dates.length, longestStreak: longest, currentStreak: current, busiestDay, busiestCount }
}

export const statsOf = (activity, today = new Date().toISOString().slice(0, 10)) =>
  activity.stats ?? computeStats(activity, today)

/** Radar chart numbers: whole-number percentages that add up to 100 (largest remainder). */
export function breakdownPercents(b) {
  const parts = [
    ['commits', b.commits], ['pullRequests', b.pullRequests], ['reviews', b.reviews],
  ]
  const total = parts.reduce((s, [, n]) => s + n, 0)
  const out = { commits: 0, pullRequests: 0, reviews: 0 }
  if (total === 0) return out
  const exact = parts.map(([k, n]) => [k, (n * 100) / total])
  exact.forEach(([k, v]) => { out[k] = Math.floor(v) })
  let left = 100 - Object.values(out).reduce((s, n) => s + n, 0)
  exact.sort((a, b) => (b[1] - Math.floor(b[1])) - (a[1] - Math.floor(a[1])))
  for (const [k] of exact) { if (left-- > 0) out[k]++ }
  return out
}

export const fmtDay = (isoDate) =>
  new Date(parse(isoDate)).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' })

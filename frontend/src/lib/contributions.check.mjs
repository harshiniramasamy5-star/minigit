// Run: node src/lib/contributions.check.mjs   (no test framework needed, same style as layoutGraph.check.mjs)
import assert from 'node:assert/strict'
import { breakdownPercents, buildCalendar, computeStats, levelFor } from './contributions.js'

// levels mirror the Java ContributionLevel enum
assert.deepEqual([0, 1, 4, 6, 8].map((n) => levelFor(n, 8)), [0, 1, 2, 3, 4])
assert.equal(levelFor(3, 0), 0)

// 2026-11-05 is a Thursday: the first column must start on Sunday 2026-11-01's week and pad with nulls
const activity = {
  from: '2026-11-05', to: '2026-11-18', total: 4,
  days: [{ date: '2026-11-05', count: 1 }, { date: '2026-11-06', count: 3 }, { date: '2026-11-07', count: 2 }, { date: '2026-11-18', count: 3 }],
}
const { weeks, months } = buildCalendar(activity)
assert.equal(weeks.length, 3)
assert.equal(weeks[0].filter(Boolean).length, 3)                 // Thu, Fri, Sat only
assert.equal(weeks[0][4].date, '2026-11-05')                     // Thursday is row 4 (Sun = 0)
assert.equal(weeks[2][3].date, '2026-11-18')                     // Wednesday
assert.equal(weeks[2][4], null)                                  // after `to`
assert.equal(weeks[0][5].level, 4)                               // busiest day (3) is full shade
assert.equal(months[0].label, 'Nov')

// year view: 53 columns, one label per month, labels never closer than 3 columns
const year = buildCalendar({ from: '2026-01-01', to: '2026-12-31', days: [] })
assert.equal(year.weeks.length, 53)
assert.equal(year.months.length, 12)
year.months.slice(1).forEach((m, i) => assert.ok(m.col - year.months[i].col >= 3))
assert.equal(year.weeks.flat().filter(Boolean).length, 365)

// stats mirror ContributionCalendarTest.java
const s = computeStats({
  from: '2026-01-01', to: '2026-12-31',
  days: [{ date: '2026-03-01', count: 1 }, { date: '2026-03-02', count: 5 }, { date: '2026-03-03', count: 2 }, { date: '2026-03-10', count: 5 }],
}, '2026-03-11')
assert.deepEqual(s, { activeDays: 4, longestStreak: 3, currentStreak: 1, busiestDay: '2026-03-02', busiestCount: 5 })

// percentages always add up to 100
for (const b of [{ commits: 94, pullRequests: 4, reviews: 2 }, { commits: 1, pullRequests: 1, reviews: 1 }, { commits: 0, pullRequests: 0, reviews: 0 }]) {
  const p = breakdownPercents(b)
  const sum = p.commits + p.pullRequests + p.reviews
  assert.equal(sum, b.commits + b.pullRequests + b.reviews === 0 ? 0 : 100)
}

console.log('contributions.js checks passed')

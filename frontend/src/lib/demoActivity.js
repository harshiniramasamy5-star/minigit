// Sample GET /activity data so the heatmap can be previewed without a database: open /?demo=1
// Deterministic (seeded) so it looks the same on every reload.
import { computeStats, levelFor } from './contributions'

const DAY = 86_400_000

function rng(seed) {
  let s = seed >>> 0
  return () => {
    s = (s + 0x6d2b79f5) >>> 0
    let t = s
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function demoActivity(year) {
  const now = new Date()
  const todayMs = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())
  const fromMs = year == null ? todayMs - 364 * DAY : Date.UTC(year, 0, 1)
  const toMs = year == null ? todayMs : Math.min(Date.UTC(year, 11, 31), todayMs)
  const rand = rng(year ?? 7)

  const days = []
  for (let ms = fromMs; ms <= toMs; ms += DAY) {
    const weekday = new Date(ms).getUTCDay()
    const chance = weekday === 0 || weekday === 6 ? 0.18 : 0.62
    if (rand() > chance) continue
    days.push({ date: new Date(ms).toISOString().slice(0, 10), count: 1 + Math.floor(rand() * rand() * 9) })
  }
  const busiest = days.reduce((m, d) => Math.max(m, d.count), 0)
  days.forEach((d) => { d.level = levelFor(d.count, busiest) })

  const total = days.reduce((s, d) => s + d.count, 0)
  const commits = Math.round(total * 0.88)
  const pullRequests = Math.round(total * 0.08)
  const reviews = total - commits - pullRequests
  const activity = {
    from: new Date(fromMs).toISOString().slice(0, 10),
    to: new Date(year == null ? todayMs : Date.UTC(year, 11, 31)).toISOString().slice(0, 10),
    total, years: [now.getUTCFullYear(), now.getUTCFullYear() - 1, now.getUTCFullYear() - 2],
    days, breakdown: { commits, pullRequests, reviews },
    repos: [
      { repo: 'harshini/minigit', count: Math.round(total * 0.5) }, { repo: 'aakriti/merge-engine', count: Math.round(total * 0.25) },
      { repo: 'arisha/diff-viewer', count: Math.round(total * 0.15) }, { repo: 'shrejal/auth-service', count: Math.round(total * 0.06) },
      { repo: 'harshini/notes', count: Math.round(total * 0.04) },
    ],
  }
  activity.stats = computeStats(activity, new Date(todayMs).toISOString().slice(0, 10))
  return activity
}

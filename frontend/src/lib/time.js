// "3 hours ago" style text for commit rows. Falls back to a date for anything older than a month.
export function timeAgo(isoString, now = Date.now()) {
  if (!isoString) return ''
  const diff = Math.max(0, now - new Date(isoString).getTime())
  const units = [['year', 31_536_000_000], ['month', 2_592_000_000], ['day', 86_400_000], ['hour', 3_600_000], ['minute', 60_000]]
  for (const [name, ms] of units) {
    const n = Math.floor(diff / ms)
    if (n >= 1) {
      if (name === 'year' || name === 'month') return `on ${new Date(isoString).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
      return `${n} ${name}${n === 1 ? '' : 's'} ago`
    }
  }
  return 'just now'
}

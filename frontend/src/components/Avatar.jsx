// Round initial-letter avatar. The colour comes from the name, so a person always looks the same.
const PALETTE = ['#2f81f7', '#3fb950', '#d29922', '#a371f7', '#db61a2', '#39c5cf', '#f0883e', '#f85149']

export const colorFor = (name = '') => {
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return PALETTE[h % PALETTE.length]
}

export default function Avatar({ name = '?', size = 24, className = '' }) {
  return (
    <span
      aria-hidden
      className={`inline-flex shrink-0 select-none items-center justify-center rounded-full font-semibold text-white ${className}`}
      style={{ width: size, height: size, background: colorFor(name), fontSize: Math.max(10, size * 0.45) }}
    >
      {name.trim().charAt(0).toUpperCase() || '?'}
    </span>
  )
}

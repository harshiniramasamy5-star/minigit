import { useCallback, useEffect, useState } from 'react'

const KEY = 'minigit.theme'

const read = () => {
  try { return localStorage.getItem(KEY) === 'light' ? 'light' : 'dark' } catch { return 'dark' }
}

// Small hook: current theme + toggle. index.html already applied the saved theme before first paint.
export function useTheme() {
  const [theme, setTheme] = useState(read)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try { localStorage.setItem(KEY, theme) } catch { /* storage blocked: theme just won't persist */ }
  }, [theme])

  const toggle = useCallback(() => setTheme((t) => (t === 'dark' ? 'light' : 'dark')), [])
  return { theme, toggle }
}

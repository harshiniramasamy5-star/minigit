/** @type {import('tailwindcss').Config} */
// Shared tokens. Use these names in every page so the app looks like one app.
// Every colour reads a CSS variable (see index.css) so the whole app switches between GitHub's
// dark and light palettes. `<alpha-value>` keeps opacity utilities such as border-danger/30 working.
const v = (name) => `rgb(var(--c-${name}) / <alpha-value>)`

export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: ['selector', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        ink: v('ink'),            // main text
        paper: v('paper'),        // page background
        surface: v('surface'),    // cards, inputs, boxes
        header: v('header'),      // top bar
        line: v('line'),          // borders
        muted: v('muted'),        // secondary text
        link: v('link'),          // links
        accent: v('accent'),      // selected pill (year picker, active tab underline)
        commit: { DEFAULT: v('commit'), dark: v('commit-dark'), soft: v('commit-soft') },  // primary green button
        merged: v('merged'),      // PR merged state
        conflict: v('conflict'),  // conflicts, warnings
        danger: v('danger'),      // destructive, errors
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'system-ui', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],   // hashes, paths, diffs only
      },
      borderRadius: { md: '6px', lg: '8px' },
      boxShadow: {
        pop: '0 8px 24px rgb(1 4 9 / 0.35)',
      },
    },
  },
  plugins: [],
}

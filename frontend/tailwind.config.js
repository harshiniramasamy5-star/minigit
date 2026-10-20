/** @type {import('tailwindcss').Config} */
// Shared tokens. Use these names in every page so the app looks like one app.
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#1A2433',       // text, top bar
        paper: '#F6F7F9',     // page background
        line: '#DDE1E7',      // borders
        muted: '#5B6675',     // secondary text
        commit: { DEFAULT: '#1F6F78', dark: '#17565D', soft: '#E3F0F1' },  // primary actions
        merged: '#6B4FA8',    // PR merged state
        conflict: '#B7791F',  // conflicts, warnings
        danger: '#B42318',    // destructive, errors
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', 'system-ui', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'ui-monospace', 'monospace'],   // hashes, paths, diffs only
      },
    },
  },
  plugins: [],
}

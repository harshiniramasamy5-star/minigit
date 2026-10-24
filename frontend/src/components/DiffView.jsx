const CONTEXT = 3
const STATUS_TONE = { ADDED: 'text-commit', DELETED: 'text-danger', MODIFIED: 'text-conflict' }
const LINE_TONE = { INSERT: 'bg-commit-soft', DELETE: 'bg-red-50', EQUAL: '' }
const MARK = { INSERT: '+', DELETE: '-', EQUAL: ' ' }

// Keeps every changed line plus CONTEXT lines around it; folds the rest into "… N unchanged lines".
function fold(lines) {
  const keep = new Array(lines.length).fill(false)
  lines.forEach((l, i) => {
    if (l.type !== 'EQUAL') {
      for (let j = Math.max(0, i - CONTEXT); j <= Math.min(lines.length - 1, i + CONTEXT); j++) keep[j] = true
    }
  })
  const out = []
  let hidden = 0
  lines.forEach((l, i) => {
    if (keep[i]) {
      if (hidden) { out.push({ gap: hidden }); hidden = 0 }
      out.push({ line: l })
    } else {
      hidden++
    }
  })
  if (hidden) out.push({ gap: hidden })
  return out
}

// files = the /diff response: [{path, status, binary, lines: [{type: EQUAL|INSERT|DELETE, text}]}]
export default function DiffView({ files, actions }) {
  if (!files.length) return <p className="text-sm text-muted">No file changes.</p>
  return (
    <div className="space-y-4">
      {files.map((f) => (
        <section key={f.path} className="overflow-hidden rounded-lg border border-line bg-white">
          <header className="flex items-center gap-3 border-b border-line bg-paper px-3 py-2 text-sm">
            <span className="font-mono">{f.path}</span>
            <span className={`text-xs font-medium ${STATUS_TONE[f.status]}`}>{f.status}</span>
            <span className="ml-auto">{actions?.(f)}</span>
          </header>
          {f.binary ? (
            <p className="p-3 text-sm text-muted">Binary or very large file, no preview.</p>
          ) : (
            <div className="overflow-x-auto font-mono text-xs leading-5">
              {fold(f.lines).map((row, i) => row.gap ? (
                <div key={i} className="bg-paper px-3 text-muted">… {row.gap} unchanged line{row.gap === 1 ? '' : 's'}</div>
              ) : (
                <div key={i} className={`whitespace-pre px-3 ${LINE_TONE[row.line.type]}`}>
                  <span className="mr-2 select-none text-muted">{MARK[row.line.type]}</span>{row.line.text}
                </div>
              ))}
            </div>
          )}
        </section>
      ))}
    </div>
  )
}

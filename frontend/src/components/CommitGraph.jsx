import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { layoutGraph } from '../lib/layoutGraph'

const ROW = 40       // px per commit row
const LANE = 18      // px between lanes
const PAD = 10
const COLORS = ['#2563eb', '#16a34a', '#d97706', '#9333ea', '#db2777', '#0891b2', '#65a30d', '#dc2626']
const color = (lane) => COLORS[lane % COLORS.length]

// commits: newest first, [{hash, parents, message, author, createdAt}]
// branches: [{name, head}]
export default function CommitGraph({ commits, branches = [] }) {
  const { rows, lanes } = useMemo(() => layoutGraph(commits), [commits])
  const labels = useMemo(() => {
    const m = new Map()
    branches.forEach((b) => m.set(b.head, [...(m.get(b.head) ?? []), b.name]))
    return m
  }, [branches])

  const width = Math.max(1, lanes) * LANE + PAD * 2
  const x = (lane) => PAD + lane * LANE + LANE / 2

  return (
    <div className="flex overflow-x-auto rounded-lg border border-line bg-white">
      <svg width={width} height={rows.length * ROW} className="shrink-0" aria-hidden="true">
        {rows.map((row, i) => {
          const top = i * ROW
          return (
            <g key={row.commit.hash}>
              {row.segments.map((s, j) => {
                const x1 = x(s.x1), y1 = top + s.y1 * ROW, x2 = x(s.x2), y2 = top + s.y2 * ROW
                const d = x1 === x2
                  ? `M${x1} ${y1} L${x2} ${y2}`
                  : `M${x1} ${y1} C${x1} ${(y1 + y2) / 2} ${x2} ${(y1 + y2) / 2} ${x2} ${y2}`
                return <path key={j} d={d} stroke={color(s.lane)} strokeWidth="2" fill="none" />
              })}
              <circle cx={x(row.col)} cy={top + ROW / 2} r={row.commit.parents?.length > 1 ? 6 : 5}
                fill={row.commit.parents?.length > 1 ? 'white' : color(row.col)}
                stroke={color(row.col)} strokeWidth="2.5" />
            </g>
          )
        })}
      </svg>

      <ol className="min-w-0 flex-1">
        {rows.map(({ commit }) => (
          <li key={commit.hash} style={{ height: ROW }} className="flex items-center gap-3 pr-4 text-sm">
            <Link to={`../commits/${commit.hash}`} relative="path" className="hash shrink-0 hover:underline">
              {commit.hash.slice(0, 7)}
            </Link>
            {labels.get(commit.hash)?.map((name) => (
              <span key={name} className="shrink-0 rounded-full border border-line px-2 py-0.5 font-mono text-xs">{name}</span>
            ))}
            <span className="truncate">{commit.message}</span>
            <span className="ml-auto shrink-0 text-xs text-muted">
              {commit.author}{commit.createdAt && ` · ${new Date(commit.createdAt).toLocaleDateString()}`}
            </span>
          </li>
        ))}
      </ol>
    </div>
  )
}

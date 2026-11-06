import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { layoutGraph } from '../lib/layoutGraph'
import { timeAgo } from '../lib/time'
import Avatar from './Avatar'

const ROW = 56       // px per commit row: room for a 2-line message + author line
const LANE = 20      // px between lanes
const PAD = 12
const COLORS = ['#3fb950', '#58a6ff', '#d29922', '#a371f7', '#db61a2', '#39c5cf', '#f0883e', '#f85149']
const color = (lane) => COLORS[lane % COLORS.length]

// commits: newest first, [{hash, parents, message, author, createdAt}]
// branches: [{name, head}]
// Left: the lane drawing (one dot per commit, curves for branches and merges).
// Right: every commit's full message, author, age and short hash, row-aligned with its dot.
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
    <div className="box flex overflow-x-auto">
      <svg width={width} height={rows.length * ROW} className="shrink-0" aria-hidden="true">
        {rows.map((row, i) => {
          const top = i * ROW
          const isMerge = row.commit.parents?.length > 1
          return (
            <g key={row.commit.hash}>
              {row.segments.map((s, j) => {
                const x1 = x(s.x1), y1 = top + s.y1 * ROW, x2 = x(s.x2), y2 = top + s.y2 * ROW
                const d = x1 === x2
                  ? `M${x1} ${y1} L${x2} ${y2}`
                  : `M${x1} ${y1} C${x1} ${(y1 + y2) / 2} ${x2} ${(y1 + y2) / 2} ${x2} ${y2}`
                return <path key={j} d={d} stroke={color(s.lane)} strokeWidth="2" fill="none" />
              })}
              <circle cx={x(row.col)} cy={top + ROW / 2} r={isMerge ? 6 : 5}
                style={{ fill: isMerge ? 'rgb(var(--c-surface))' : color(row.col) }}
                stroke={color(row.col)} strokeWidth="2.5" />
            </g>
          )
        })}
      </svg>

      <ol className="min-w-[24rem] flex-1">
        {rows.map(({ commit }) => {
          const isMerge = commit.parents?.length > 1
          return (
            <li key={commit.hash} style={{ height: ROW }} className="flex items-center gap-3 border-b border-line/60 pr-4 text-sm last:border-b-0">
              <div className="min-w-0 flex-1">
                <p className="line-clamp-2 break-words font-medium leading-[18px]" title={commit.message}>
                  {labels.get(commit.hash)?.map((name) => (
                    <span key={name} className="mr-2 inline-block rounded-full border border-commit/60 bg-commit/15 px-2 py-px align-middle font-mono text-xs font-normal text-ink">{name}</span>
                  ))}
                  {isMerge && <span className="mr-2 inline-block rounded-full border border-merged/60 bg-merged/15 px-2 py-px align-middle text-xs font-normal text-ink">merge</span>}
                  {commit.message}
                </p>
                <p className="mt-0.5 flex items-center gap-1.5 truncate text-xs text-muted">
                  <Avatar name={commit.author} size={16} />
                  <span className="font-medium text-ink/80">{commit.author}</span>
                  {commit.createdAt && <span title={new Date(commit.createdAt).toLocaleString()}>committed {timeAgo(commit.createdAt)}</span>}
                </p>
              </div>
              <Link to={`../commits/${commit.hash}`} relative="path"
                className="hash shrink-0 rounded-md border border-line px-2 py-1 hover:border-link hover:text-link"
                title={commit.hash}>
                {commit.hash.slice(0, 7)}
              </Link>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

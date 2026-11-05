import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import DiffView from '../components/DiffView'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { shortHash, useRepo, when } from '../lib/useRepo'
import { BADGE } from './PullsPage'

// U8: one pull request: what it changes, approvals, comments, merge and close.
export default function PullDetailPage() {
  const { owner, repo: name, number } = useParams()
  const { repo, canWrite } = useRepo(owner, name)
  const { user } = useAuth()
  const toast = useToast()
  const base = `/repos/${owner}/${name}/pulls/${number}`
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [conflict, setConflict] = useState(null)   // session id when a merge hit conflicts
  const [needsRule, setNeedsRule] = useState(false) // target branch requires an approved pull request

  const load = useCallback(() => {
    api.get(base, { silent: true }).then((r) => { setData(r.data); setError(null) }).catch(setError)
  }, [base])
  useEffect(load, [load])

  useEffect(() => {
    if (!repo || !data) return
    api.get(`/repos/${repo.id}/branches/protection`, { silent: true })
      .then((r) => setNeedsRule(r.data.includes(data.pr.target))).catch(() => setNeedsRule(false))
  }, [repo, data])

  const run = async (fn) => {
    setBusy(true)
    try { await fn() } catch { /* toast shown */ } finally { setBusy(false) }
  }

  const approve = () => run(async () => { await api.post(`${base}/approve`); toast.success('Approved'); load() })
  const close = () => run(async () => { await api.post(`${base}/close`); toast.info('Pull request closed'); load() })
  const addComment = () => run(async () => {
    await api.post(`${base}/comments`, { body: comment })
    setComment(''); load()
  })
  const merge = () => run(async () => {
    const { data: out } = await api.post(`${base}/merge`, {})
    if (out.result === 'CONFLICTS') {
      setConflict(out.sessionId)
    } else {
      toast.success(out.result === 'UP_TO_DATE' ? 'Nothing to merge, marked as merged' : `Merged (${out.result.toLowerCase().replace('_', ' ')})`)
      setConflict(null)
    }
    load()
  })

  if (error) return <p className="text-sm text-danger">{error.message}</p>
  if (!data) return <p className="text-sm text-muted">Loading…</p>
  const { pr, diff, comments } = data
  const open = pr.status === 'OPEN'
  const isAuthor = user?.username === pr.author
  const approvedByMe = pr.approvals.includes(user?.username)
  const blocked = needsRule && !pr.approvals.some((a) => a !== pr.author)

  return (
    <div className="space-y-6">
      <div>
        <Link to=".." relative="path" className="text-sm text-commit hover:underline">← All pull requests</Link>
        <h2 className="mt-2 text-lg font-semibold">{pr.title} <span className="font-normal text-muted">#{pr.number}</span></h2>
        <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
          <span className={`rounded border px-1.5 text-xs ${BADGE[pr.status]}`}>{pr.status}</span>
          {pr.author} wants to merge <span className="font-mono">{pr.source}</span> into <span className="font-mono">{pr.target}</span> · {when(pr.createdAt)}
        </p>
        {pr.mergeCommit && (
          <p className="mt-1 text-sm">Merged as <Link to={`../../commits/${pr.mergeCommit}`} relative="path" className="hash hover:underline">{shortHash(pr.mergeCommit)}</Link></p>
        )}
        {pr.description && <p className="mt-3 whitespace-pre-wrap text-sm">{pr.description}</p>}
      </div>

      <section className="rounded-lg border border-line bg-white p-4" aria-labelledby="review">
        <h3 id="review" className="text-sm font-semibold">Review</h3>
        <p className="mt-1 text-sm text-muted">
          {pr.approvals.length ? `Approved by ${pr.approvals.join(', ')}.` : 'No approvals yet.'} {needsRule
            ? `${pr.target} is protected: it needs one approval from someone other than the author.`
            : 'Approval is recorded but not required to merge.'}
        </p>
        {open && canWrite && (
          <div className="mt-3 flex flex-wrap gap-2">
            <button className="btn-secondary" disabled={busy || isAuthor || approvedByMe} onClick={approve}
              title={isAuthor ? 'You can\'t approve your own pull request' : undefined}>
              {approvedByMe ? 'You approved' : 'Approve'}
            </button>
            <button className="btn-primary" disabled={busy || blocked} onClick={merge}
              title={blocked ? `${pr.target} needs an approval from someone other than the author` : undefined}>Merge</button>
          </div>
        )}
        {open && (canWrite || isAuthor) && (
          <button className="mt-2 text-sm text-danger hover:underline" disabled={busy} onClick={close}>Close without merging</button>
        )}
        {conflict && (
          <p className="mt-3 rounded border border-conflict/50 p-2 text-sm text-conflict" role="alert">
            This merge has conflicts, so nothing was merged yet. The pull request stays open and turns merged when you
            finish the merge.{' '}
            <Link to={`../../merges/${conflict}`} relative="path" className="font-medium underline">Resolve the conflicts</Link>
          </p>
        )}
      </section>

      {open && (
        <section>
          <h3 className="mb-3 text-sm font-semibold">Changes</h3>
          <DiffView files={diff} />
        </section>
      )}

      <section aria-labelledby="comments">
        <h3 id="comments" className="mb-3 text-sm font-semibold">Comments ({comments.length})</h3>
        {comments.length > 0 && (
          <ul className="space-y-3">
            {comments.map((c) => (
              <li key={c.id} className="rounded-lg border border-line bg-white p-3">
                <p className="text-xs text-muted">{c.author} · {when(c.createdAt)}</p>
                <p className="mt-1 whitespace-pre-wrap text-sm">{c.body}</p>
              </li>
            ))}
          </ul>
        )}
        {open && repo?.myRole && (
          <div className="mt-3 space-y-2">
            <label htmlFor="comment" className="sr-only">Add a comment</label>
            <textarea id="comment" className="input h-24" maxLength={5000} placeholder="Leave a comment" value={comment}
              onChange={(e) => setComment(e.target.value)} />
            <button className="btn-primary" disabled={busy || !comment.trim()} onClick={addComment}>Comment</button>
          </div>
        )}
        {open && !repo?.myRole && <p className="mt-2 text-sm text-muted">Only collaborators can comment.</p>}
      </section>
    </div>
  )
}

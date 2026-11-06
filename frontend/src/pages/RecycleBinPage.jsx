import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useToast } from '../context/ToastContext'

const DAY = 24 * 60 * 60 * 1000
const daysLeft = (iso) => Math.max(0, Math.ceil((Date.parse(iso) - Date.now()) / DAY))

// U11: deleted repositories and branches wait here for 7 days.
export default function RecycleBinPage() {
  const toast = useToast()
  const [items, setItems] = useState(null)
  const [error, setError] = useState(null)
  const [confirming, setConfirming] = useState(null)   // id of the item whose "delete forever" is armed
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(() => {
    api.get('/recycle-bin', { silent: true }).then((res) => setItems(res.data)).catch(setError)
  }, [])
  useEffect(load, [load])

  const act = async (item, kind) => {
    setBusyId(item.id)
    try {
      if (kind === 'restore') {
        await api.post(`/recycle-bin/${item.id}/restore`)
        toast.success(`Restored ${item.name}`)
      } else {
        await api.delete(`/recycle-bin/${item.id}`)
        toast.success(`${item.name} deleted for good`)
      }
      setConfirming(null)
      load()
    } catch {
      // the API client already showed the error toast
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Recycle bin</h1>
      <p className="mt-1 text-sm text-muted">Deleted repositories and branches are kept for 7 days, then removed automatically.</p>

      {error && <p className="mt-6 text-sm text-danger">{error.message}</p>}
      {!items && !error && <p className="mt-6 text-sm text-muted">Loading…</p>}
      {items && items.length === 0 && (
        <div className="mt-6 rounded-lg border border-line bg-surface p-8 text-sm text-muted">Nothing here.</div>
      )}
      {items && items.length > 0 && (
        <ul className="mt-4 divide-y divide-line rounded-lg border border-line bg-surface">
          {items.map((it) => (
            <li key={it.id} className="flex flex-wrap items-center gap-3 p-4">
              <div className="min-w-0 flex-1">
                <p className="truncate">
                  <span className="mr-2 rounded border border-line px-1.5 text-xs text-muted">{it.itemType === 'BRANCH' ? 'Branch' : 'Repository'}</span>
                  <span className="font-medium">{it.name}</span>
                  {it.itemType === 'BRANCH' && <span className="text-muted"> in <Link to={`/${it.repo}`} className="hover:underline">{it.repo}</Link></span>}
                </p>
                <p className="mt-0.5 text-xs text-muted">
                  Deleted {new Date(it.deletedAt).toLocaleDateString()} · {daysLeft(it.expiresAt)} day(s) left
                </p>
              </div>
              <button className="btn-secondary" disabled={busyId === it.id} onClick={() => act(it, 'restore')}>Restore</button>
              {confirming === it.id ? (
                <>
                  <button className="btn-danger" disabled={busyId === it.id} onClick={() => act(it, 'purge')}>Yes, delete forever</button>
                  <button className="btn-secondary" onClick={() => setConfirming(null)}>Cancel</button>
                </>
              ) : (
                <button className="btn-secondary text-danger" onClick={() => setConfirming(it.id)}>Delete forever</button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import RepoCard from '../components/RepoCard'

// Public repo search (guests allowed). GET /explore?q=&page=
export default function ExplorePage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? 0)
  const [input, setInput] = useState(q)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    api.get('/explore', { params: { q, page, size: 20 } })
      .then((res) => setResult(res.data))
      .catch(() => setResult(null))
      .finally(() => setLoading(false))
  }, [q, page])

  const submit = (e) => {
    e.preventDefault()
    setParams(input.trim() ? { q: input.trim() } : {})
  }

  const lastPage = result ? Math.max(0, Math.ceil(result.total / result.size) - 1) : 0

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-semibold tracking-tight">Explore public repositories</h1>
      <form onSubmit={submit} className="mt-4 flex gap-2" role="search">
        <label htmlFor="q" className="sr-only">Search repositories</label>
        <input id="q" className="input" placeholder="Search by name or description" value={input} onChange={(e) => setInput(e.target.value)} />
        <button className="btn-primary" type="submit">Search</button>
      </form>

      {loading && <p className="mt-6 text-sm text-muted">Searching…</p>}
      {!loading && result && result.items.length === 0 && (
        <p className="mt-6 text-sm text-muted">{q ? `No public repositories match "${q}".` : 'No public repositories yet.'}</p>
      )}
      {!loading && result && result.items.length > 0 && (
        <>
          <p className="mt-6 text-sm text-muted">{result.total} {result.total === 1 ? 'repository' : 'repositories'}</p>
          <ul className="divide-y divide-line">
            {result.items.map((r) => <RepoCard key={r.id} repo={r} />)}
          </ul>
          {lastPage > 0 && (
            <div className="mt-4 flex items-center gap-3 text-sm">
              <button className="btn-secondary" disabled={page === 0} onClick={() => setParams({ ...(q && { q }), page: page - 1 })}>Previous</button>
              <span className="text-muted">Page {page + 1} of {lastPage + 1}</span>
              <button className="btn-secondary" disabled={page >= lastPage} onClick={() => setParams({ ...(q && { q }), page: page + 1 })}>Next</button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

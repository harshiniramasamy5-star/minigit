import { useEffect, useState } from 'react'
import { api } from '../api/client'

// Loads {id, myRole, defaultBranch, ...} for the repo in the URL. canWrite = Write or Owner.
export function useRepo(owner, name) {
  const [repo, setRepo] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    setRepo(null)
    setError(null)
    api.get(`/repos/${owner}/${name}`, { silent: true }).then((res) => setRepo(res.data)).catch(setError)
  }, [owner, name])

  return { repo, error, canWrite: repo?.myRole === 'WRITE' || repo?.myRole === 'OWNER' }
}

export const shortHash = (h) => (h ? h.slice(0, 7) : '')
export const when = (iso) => new Date(iso).toLocaleString()

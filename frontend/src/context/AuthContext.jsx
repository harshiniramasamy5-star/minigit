import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, DEV_USER_KEY, EXPIRES_KEY, onSessionExpired, TOKEN_KEY } from '../api/client'
import { useToast } from './ToastContext'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(
    Boolean(localStorage.getItem(TOKEN_KEY) || localStorage.getItem(DEV_USER_KEY)))
  const navigate = useNavigate()
  const toast = useToast()

  const clear = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EXPIRES_KEY)
    localStorage.removeItem(DEV_USER_KEY)
    setUser(null)
  }, [])

  const logout = useCallback(() => { clear(); navigate('/login') }, [clear, navigate])

  const expire = useCallback(() => {
    const wasDev = !localStorage.getItem(TOKEN_KEY) && localStorage.getItem(DEV_USER_KEY)
    if (!localStorage.getItem(TOKEN_KEY) && !wasDev) return
    clear()
    toast.info(wasDev
      ? 'Dev login ended (dev mode is off or that user no longer exists). Log in again.'
      : 'Your session ended after 30 minutes. Log in again to continue.')
    navigate('/login')
  }, [clear, navigate, toast])

  // Restore session on reload
  useEffect(() => {
    if (localStorage.getItem(TOKEN_KEY)) {
      api.get('/auth/me', { silent: true })
        .then(({ data }) => setUser(data))
        .catch(() => clear())
        .finally(() => setLoading(false))
      return
    }
    const devUser = localStorage.getItem(DEV_USER_KEY)
    if (!devUser) return
    // Keep the dev login only while the backend still runs in open-dev mode.
    api.get('/health', { silent: true })
      .then(({ data }) => (data.devAuth ? setUser({ username: devUser, dev: true }) : clear()))
      .catch(() => clear())
      .finally(() => setLoading(false))
  }, [clear])

  // Fixed 30-min JWT: log out at expiry instead of waiting for a 401
  useEffect(() => {
    if (!user) return
    const expiresAt = Date.parse(localStorage.getItem(EXPIRES_KEY) || '')
    if (Number.isNaN(expiresAt)) return
    const timer = setTimeout(expire, Math.max(0, expiresAt - Date.now()))
    return () => clearTimeout(timer)
  }, [user, expire])

  useEffect(() => onSessionExpired(expire), [expire])

  const login = async (usernameOrEmail, password) => {
    const { data } = await api.post('/auth/login', { usernameOrEmail, password }, { silent: true })
    localStorage.removeItem(DEV_USER_KEY)
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(EXPIRES_KEY, data.expiresAt)
    setUser(data.user)
    return data.user
  }

  // TEMPORARY until U1 auth is merged. Works only while the backend has open-dev on.
  // The user must already exist in the users table (see docs/integration-notes.md).
  const devLogin = async (username) => {
    const name = username.trim()
    try {
      await api.get('/dashboard', { headers: { 'X-Dev-User': name }, silent: true })
    } catch (err) {
      if (err.status === 401) {
        throw new Error(`No user "${name}" in the database. Add one with the SQL in docs/integration-notes.md.`)
      }
      throw err
    }
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EXPIRES_KEY)
    localStorage.setItem(DEV_USER_KEY, name)
    setUser({ username: name, dev: true })
    return name
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, devLogin, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)

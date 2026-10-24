import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, EXPIRES_KEY, onSessionExpired, TOKEN_KEY } from '../api/client'
import { useToast } from './ToastContext'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(Boolean(localStorage.getItem(TOKEN_KEY)))
  const navigate = useNavigate()
  const toast = useToast()

  const clear = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EXPIRES_KEY)
    setUser(null)
  }, [])

  const logout = useCallback(() => { clear(); navigate('/login') }, [clear, navigate])

  const expire = useCallback(() => {
    if (!localStorage.getItem(TOKEN_KEY)) return
    clear()
    toast.info('Your session ended after 30 minutes. Log in again to continue.')
    navigate('/login')
  }, [clear, navigate, toast])

  // Restore session on reload
  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) return
    api.get('/auth/me', { silent: true })
      .then(({ data }) => setUser(data))
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
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(EXPIRES_KEY, data.expiresAt)
    setUser(data.user)
    return data.user
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)

import axios from 'axios'

// One axios instance for the whole app. Import { api } in your pages:
//   const { data } = await api.get(`/repos/${owner}/${repo}/branches`)
// Errors are normalised to the backend ApiError shape and shown as a toast automatically.
// Pass { silent: true } when your page handles the error itself, e.g. a 409 BRANCH_HEAD_MOVED dialog:
//   try { await api.post(url, body, { silent: true }) } catch (err) { if (err.code === 'BRANCH_HEAD_MOVED') ... }

export const TOKEN_KEY = 'minigit.token'
export const EXPIRES_KEY = 'minigit.expiresAt'

export const api = axios.create({ baseURL: '/api/v1', timeout: 20000 })

const errorListeners = new Set()
const expiredListeners = new Set()

export const onApiError = (fn) => { errorListeners.add(fn); return () => errorListeners.delete(fn) }
export const onSessionExpired = (fn) => { expiredListeners.add(fn); return () => expiredListeners.delete(fn) }

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (error) => {
    const apiError = normalise(error)
    const isLogin = error.config?.url?.startsWith('/auth/login')

    if (apiError.status === 401 && !isLogin) {
      expiredListeners.forEach((fn) => fn())
    } else if (!error.config?.silent) {
      errorListeners.forEach((fn) => fn(apiError))
    }
    return Promise.reject(apiError)
  },
)

function normalise(error) {
  if (error.response?.data?.code) {
    const err = new Error(error.response.data.message)
    return Object.assign(err, error.response.data)          // status, code, message, fieldErrors
  }
  if (error.response) {
    return Object.assign(new Error(`Request failed (${error.response.status})`),
      { status: error.response.status, code: 'HTTP_ERROR', fieldErrors: [] })
  }
  return Object.assign(new Error("Can't reach the server. Check that the backend is running."),
    { status: 0, code: 'NETWORK_ERROR', fieldErrors: [] })
}

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { onApiError } from '../api/client'

const ToastContext = createContext(null)
const TONES = {
  error: 'border-danger/30 bg-surface text-ink [&>span]:bg-danger',
  success: 'border-commit/30 bg-surface text-ink [&>span]:bg-commit',
  info: 'border-line bg-surface text-ink [&>span]:bg-muted',
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const nextId = useRef(1)

  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), [])

  const show = useCallback((message, tone = 'info') => {
    const id = nextId.current++
    setToasts((t) => [...t.slice(-3), { id, message, tone }])   // max 4 on screen
    setTimeout(() => dismiss(id), tone === 'error' ? 7000 : 4000)
  }, [dismiss])

  useEffect(() => onApiError((err) => show(err.message, 'error')), [show])

  const toast = {
    error: (m) => show(m, 'error'),
    success: (m) => show(m, 'success'),
    info: (m) => show(m, 'info'),
  }

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`flex items-start gap-3 rounded-md border p-3 text-sm shadow-sm ${TONES[t.tone]}`}>
            <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full" aria-hidden />
            <p className="flex-1">{t.message}</p>
            <button onClick={() => dismiss(t.id)} className="text-muted hover:text-ink" aria-label="Dismiss">×</button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)

import { useState } from 'react'

// Drop-in replacement for <input type="password">: same props, plus a show/hide eye button
// and a Caps Lock warning. Everything else (id, value, onChange, required, minLength...) passes through.
export default function PasswordInput({ className = '', onKeyUp, onBlur, ...props }) {
  const [visible, setVisible] = useState(false)
  const [capsOn, setCapsOn] = useState(false)

  return (
    <div>
      <div className="relative">
        <input
          {...props}
          type={visible ? 'text' : 'password'}
          className={`input pr-10 ${className}`}
          onKeyUp={(e) => { setCapsOn(e.getModifierState?.('CapsLock') ?? false); onKeyUp?.(e) }}
          onBlur={(e) => { setCapsOn(false); onBlur?.(e) }}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          title={visible ? 'Hide password' : 'Show password'}
          className="absolute inset-y-0 right-0 flex w-10 items-center justify-center rounded-r-md text-muted hover:text-ink"
        >
          {visible ? <EyeOffIcon /> : <EyeIcon />}
        </button>
      </div>
      {capsOn && <p className="mt-1 text-xs text-conflict" role="status">Caps Lock is on</p>}
    </div>
  )
}

function EyeIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M17.94 17.94A10.9 10.9 0 0 1 12 19C5 19 1 12 1 12a18.5 18.5 0 0 1 5.06-5.94M9.9 5.24A10.8 10.8 0 0 1 12 5c7 0 11 7 11 7a18.5 18.5 0 0 1-2.16 3.19M14.12 14.12a3 3 0 1 1-4.24-4.24" />
      <path d="M1 1l22 22" />
    </svg>
  )
}

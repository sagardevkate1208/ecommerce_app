import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import { CheckCircle2, Info, X, XCircle } from 'lucide-react'

const ToastContext = createContext(null)

const STYLES = {
  success: { box: 'border-green-200 bg-white', icon: <CheckCircle2 className="h-5 w-5 text-green-600" /> },
  error: { box: 'border-red-200 bg-white', icon: <XCircle className="h-5 w-5 text-red-600" /> },
  info: { box: 'border-blue-200 bg-white', icon: <Info className="h-5 w-5 text-blue-600" /> },
}

/** Small pop-up messages in the corner: toast.success('Saved'), toast.error('Failed'), toast.info('...'). */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const nextId = useRef(1)

  const dismiss = useCallback((id) => setToasts((list) => list.filter((t) => t.id !== id)), [])

  const push = useCallback(
    (type, text) => {
      const id = nextId.current++
      setToasts((list) => [...list, { id, type, text }])
      setTimeout(() => dismiss(id), type === 'error' ? 6000 : 3500)
    },
    [dismiss],
  )

  const api = useMemo(
    () => ({
      success: (text) => push('success', text),
      error: (text) => push('error', text),
      info: (text) => push('info', text),
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed right-4 top-4 z-50 flex w-80 flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            role="status"
            className={`pointer-events-auto flex items-start gap-3 rounded-lg border p-3 shadow-lg ${STYLES[t.type].box}`}
          >
            {STYLES[t.type].icon}
            <p className="flex-1 text-sm text-slate-800">{t.text}</p>
            <button onClick={() => dismiss(t.id)} aria-label="Dismiss" className="text-slate-400 hover:text-slate-700">
              <X className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)

import { useEffect, useState, useSyncExternalStore } from 'react'
import { Copy, Terminal, Trash2, X } from 'lucide-react'
import { logger } from '../utils/logger'

const COLOURS = {
  debug: 'text-slate-400',
  info: 'text-sky-300',
  warn: 'text-amber-300',
  error: 'text-red-400',
}
const ORDER = ['debug', 'info', 'warn', 'error']

/**
 * A floating log viewer. Open it with the button at the bottom left, or Ctrl+Shift+L.
 * Only shown while developing (or when VITE_DEBUG_PANEL=true), so customers never see it.
 */
export default function DebugPanel() {
  const enabled = import.meta.env.DEV || import.meta.env.VITE_DEBUG_PANEL === 'true'
  const [open, setOpen] = useState(false)
  const [minLevel, setMinLevel] = useState('debug')
  // Re-render whenever a log entry is added.
  useSyncExternalStore(logger.subscribe, logger.version)

  useEffect(() => {
    if (!enabled) return undefined
    const onKey = (e) => {
      if (e.ctrlKey && e.shiftKey && e.key.toLowerCase() === 'l') setOpen((o) => !o)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [enabled])

  if (!enabled) return null

  const all = logger.entries()
  const errors = all.filter((e) => e.level === 'error').length
  const shown = all.filter((e) => ORDER.indexOf(e.level) >= ORDER.indexOf(minLevel))

  const copy = () => {
    const text = shown
      .map((e) => `${e.time.toISOString()} ${e.level.toUpperCase()} ${e.message}${e.data === undefined ? '' : ' ' + JSON.stringify(e.data)}`)
      .join('\n')
    navigator.clipboard?.writeText(text)
  }

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} title="Open logs (Ctrl+Shift+L)"
        className="fixed bottom-4 left-4 z-50 flex items-center gap-2 rounded-full bg-slate-900 px-4 py-2 text-xs font-medium text-white shadow-lg hover:bg-slate-700">
        <Terminal className="h-4 w-4" /> Logs
        {errors > 0 && <span className="rounded-full bg-red-500 px-1.5">{errors}</span>}
      </button>
    )
  }

  return (
    <div className="fixed bottom-4 left-4 z-50 flex max-h-[65vh] w-[min(46rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-lg bg-slate-900 text-xs text-slate-100 shadow-2xl">
      <div className="flex items-center gap-2 border-b border-slate-700 px-3 py-2">
        <Terminal className="h-4 w-4" />
        <span className="font-semibold">Logs</span>
        <span className="text-slate-400">({shown.length})</span>
        <div className="ml-3 flex gap-1">
          {ORDER.map((level) => (
            <button key={level} onClick={() => setMinLevel(level)}
              className={`rounded px-2 py-0.5 ${minLevel === level ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'}`}>
              {level}
            </button>
          ))}
        </div>
        <div className="ml-auto flex gap-1">
          <button onClick={copy} title="Copy" className="rounded p-1 hover:bg-slate-700"><Copy className="h-4 w-4" /></button>
          <button onClick={logger.clear} title="Clear" className="rounded p-1 hover:bg-slate-700"><Trash2 className="h-4 w-4" /></button>
          <button onClick={() => setOpen(false)} title="Close" className="rounded p-1 hover:bg-slate-700"><X className="h-4 w-4" /></button>
        </div>
      </div>

      <div className="flex-1 overflow-auto p-2 font-mono">
        {shown.length === 0 && <p className="p-2 text-slate-500">Nothing logged yet.</p>}
        {[...shown].reverse().map((e) => (
          <details key={e.id} className="border-b border-slate-800 py-1">
            <summary className={`cursor-pointer ${COLOURS[e.level]}`}>
              <span className="text-slate-500">{e.time.toLocaleTimeString('en-GB', { hour12: false })}</span> {e.message}
            </summary>
            {e.data !== undefined && (
              <pre className="mt-1 max-h-48 overflow-auto whitespace-pre-wrap break-all rounded bg-slate-800 p-2 text-slate-200">
                {JSON.stringify(e.data, null, 2)}
              </pre>
            )}
          </details>
        ))}
      </div>
    </div>
  )
}

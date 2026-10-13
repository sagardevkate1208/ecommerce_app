// A tiny logger for the whole app.
//  - Writes to the browser console (F12 > Console).
//  - Keeps the last entries in memory so the on-screen DebugPanel can show them.
//  - Hides secrets: anything whose name looks sensitive (password, token, card number...) is replaced by ***.
//
// How much is logged is set by VITE_LOG_LEVEL (debug | info | warn | error | silent). See .env.development and
// .env.production. Without it: debug while developing, warn in a production build.

const LEVELS = { debug: 10, info: 20, warn: 30, error: 40, silent: 99 }
const configured = String(
  import.meta.env.VITE_LOG_LEVEL || (import.meta.env.DEV ? 'debug' : 'warn'),
).toLowerCase()
const threshold = LEVELS[configured] ?? LEVELS.info

const MAX_ENTRIES = 300
const entries = []
const listeners = new Set()
let version = 0
let nextId = 1

const SENSITIVE = /pass(word)?|token|authorization|secret|signature|reference|card|cvv|otp/i

/** Returns a copy of value with sensitive fields hidden. Accepts objects, arrays and JSON strings. */
export function redact(value, depth = 0) {
  if (value == null || depth > 5) return value
  if (typeof value === 'string') {
    try {
      return redact(JSON.parse(value), depth + 1)
    } catch {
      return value
    }
  }
  if (Array.isArray(value)) return value.map((v) => redact(v, depth + 1))
  if (typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, v]) => [key, SENSITIVE.test(key) ? '***' : redact(v, depth + 1)]),
    )
  }
  return value
}

const consoleFor = { debug: console.log, info: console.info, warn: console.warn, error: console.error }

function write(level, message, data) {
  if (LEVELS[level] < threshold) return

  const entry = { id: nextId++, time: new Date(), level, message, data: data === undefined ? undefined : redact(data) }
  entries.push(entry)
  if (entries.length > MAX_ENTRIES) entries.shift()
  version++
  listeners.forEach((listener) => listener())

  const stamp = entry.time.toLocaleTimeString('en-GB', { hour12: false })
  const out = consoleFor[level]
  if (entry.data === undefined) out(`[${stamp}] ${message}`)
  else out(`[${stamp}] ${message}`, entry.data)
}

export const logger = {
  level: configured,
  debug: (message, data) => write('debug', message, data),
  info: (message, data) => write('info', message, data),
  warn: (message, data) => write('warn', message, data),
  error: (message, data) => write('error', message, data),

  // used by the DebugPanel
  entries: () => entries,
  clear: () => {
    entries.length = 0
    version++
    listeners.forEach((listener) => listener())
  },
  subscribe: (listener) => {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
  version: () => version,
}

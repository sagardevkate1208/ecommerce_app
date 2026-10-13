import axios from 'axios'
import { logger } from '../utils/logger'

const TOKEN_KEY = 'shopeasy.token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// Called when the backend says our token is no longer valid. AuthContext registers its logout here.
let onUnauthorized = () => {}
export const setUnauthorizedHandler = (fn) => {
  onUnauthorized = fn
}

const describe = (config) => `${config.method?.toUpperCase()} ${config.baseURL ?? ''}${config.url ?? ''}`
const elapsed = (config) => `${Math.round(performance.now() - (config.metadata?.start ?? performance.now()))}ms`

// One client per backend service. In development vite.config.js proxies each prefix to its service.
const create = (prefix) => {
  const client = axios.create({ baseURL: `/${prefix}/api` })

  client.interceptors.request.use((config) => {
    config.metadata = { start: performance.now() }
    const token = tokenStore.get()
    if (token) config.headers.Authorization = `Bearer ${token}`
    // Bodies and query strings are logged with passwords, tokens and card numbers hidden (see utils/logger.js).
    logger.debug(`→ ${describe(config)}`, { params: config.params, body: config.data })
    return config
  })

  client.interceptors.response.use(
    (res) => {
      logger.debug(`← ${res.status} ${describe(res.config)} (${elapsed(res.config)})`, res.data)
      return res
    },
    (error) => {
      const config = error.config || {}
      if (error.response) {
        const { status, data } = error.response
        // 4xx is usually the user's doing (wrong password, not found): a warning. 5xx is a server problem: an error.
        const level = status >= 500 ? 'error' : 'warn'
        logger[level](`✖ ${status} ${describe(config)} (${elapsed(config)})`, data)
      } else {
        logger.error(`✖ no response from ${describe(config)} (${elapsed(config)}): ${error.message}`)
      }

      // A 401 on a call that sent a token means it expired. Login/register 401 means wrong credentials: leave it alone.
      const sentToken = Boolean(config.headers?.Authorization)
      if (error.response?.status === 401 && sentToken) {
        logger.warn('Session expired or token rejected: logging out')
        onUnauthorized()
      }
      return Promise.reject(error)
    },
  )
  return client
}

export const authHttp = create('auth-api')
export const catalogHttp = create('catalog-api')
export const cartHttp = create('cart-api')
export const orderHttp = create('order-api')
export const paymentHttp = create('payment-api')

/** Turns any axios error into a message a user can read. */
export function errorMessage(error) {
  const data = error?.response?.data
  if (data?.errors && typeof data.errors === 'object') {
    return Object.entries(data.errors).map(([field, msg]) => `${field}: ${msg}`).join(', ')
  }
  if (data?.message) return data.message
  if (error?.response?.status === 401) return 'Please log in again.'
  if (error?.response?.status === 403) return 'You are not allowed to do that.'
  if (error?.response?.status >= 500) return 'The server had a problem. Please try again.'
  if (error?.code === 'ERR_NETWORK' || !error?.response) return 'Cannot reach the server. Is the backend running?'
  if (error.response.status === 404) {
    return 'That endpoint was not found (HTTP 404). The backend service may be running an older version: restart it.'
  }
  return `Something went wrong (HTTP ${error.response.status}).`
}

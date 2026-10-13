import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import DebugPanel from './components/DebugPanel'
import ErrorBoundary from './components/ErrorBoundary'
import { AuthProvider } from './context/AuthContext'
import { CartProvider } from './context/CartContext'
import { ToastProvider } from './context/ToastContext'
import './index.css'
import { logger } from './utils/logger'

// Anything that escapes every try/catch still ends up in the log.
window.addEventListener('error', (e) => logger.error(`Uncaught error: ${e.message}`, { source: e.filename, line: e.lineno }))
window.addEventListener('unhandledrejection', (e) =>
  logger.error(`Unhandled promise rejection: ${e.reason?.message ?? e.reason}`),
)

logger.info(`ShopEasy started (log level: ${logger.level}, mode: ${import.meta.env.MODE})`)

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <CartProvider>
            <ErrorBoundary>
              <App />
            </ErrorBoundary>
            <DebugPanel />
          </CartProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </React.StrictMode>,
)

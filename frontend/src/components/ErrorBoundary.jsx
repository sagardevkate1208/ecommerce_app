import { Component } from 'react'
import { logger } from '../utils/logger'

/** Catches a crash inside any component, logs it, and shows a message instead of a blank white page. */
export default class ErrorBoundary extends Component {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error, info) {
    logger.error(`React crashed: ${error.message}`, { stack: error.stack, component: info.componentStack })
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div className="mx-auto max-w-md py-24 text-center">
        <h1 className="text-2xl font-bold">Something went wrong</h1>
        <p className="mt-2 text-slate-600">An unexpected error occurred. The details were written to the log.</p>
        <button className="btn-primary mt-6" onClick={() => window.location.assign('/')}>
          Back to the shop
        </button>
      </div>
    )
  }
}

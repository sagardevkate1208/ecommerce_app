import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/http'
import Alert from '../components/Alert'
import { useAuth } from '../context/AuthContext'

export default function LoginPage() {
  const { login, isLoggedIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from || null

  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (isLoggedIn) return <Navigate to={from || '/'} replace />

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const me = await login(form.email, form.password)
      // Send people back where they were going; otherwise admins land on their dashboard, customers on the shop.
      navigate(from || (me.role === 'ADMIN' ? '/admin' : '/'), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-4 text-2xl font-bold">Log in</h1>
      <form onSubmit={submit} className="card space-y-4 p-6">
        <Alert>{error}</Alert>
        <div>
          <label className="label" htmlFor="email">
            Email
          </label>
          <input id="email" className="input" type="email" required autoComplete="email" value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </div>
        <div>
          <label className="label" htmlFor="password">
            Password
          </label>
          <input id="password" className="input" type="password" required autoComplete="current-password" value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </div>
        <button className="btn-primary w-full" disabled={busy}>
          {busy ? 'Logging in...' : 'Log in'}
        </button>
        <p className="text-center text-sm text-slate-600">
          New here?{' '}
          <Link to="/register" state={{ from }} className="text-indigo-600 hover:underline">
            Create an account
          </Link>
        </p>
      </form>
    </div>
  )
}

import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/http'
import Alert from '../components/Alert'
import { useAuth } from '../context/AuthContext'

export default function RegisterPage() {
  const { register, isLoggedIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from || '/'

  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (isLoggedIn) return <Navigate to={from} replace />

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await register({ ...form, phone: form.phone || undefined })
      navigate(from, { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-4 text-2xl font-bold">Create your account</h1>
      <form onSubmit={submit} className="card space-y-4 p-6">
        <Alert>{error}</Alert>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="firstName">First name</label>
            <input id="firstName" className="input" required value={form.firstName} onChange={set('firstName')} />
          </div>
          <div>
            <label className="label" htmlFor="lastName">Last name</label>
            <input id="lastName" className="input" required value={form.lastName} onChange={set('lastName')} />
          </div>
        </div>
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" className="input" type="email" required autoComplete="email" value={form.email} onChange={set('email')} />
        </div>
        <div>
          <label className="label" htmlFor="phone">Phone (optional)</label>
          <input id="phone" className="input" value={form.phone} onChange={set('phone')} />
        </div>
        <div>
          <label className="label" htmlFor="password">Password</label>
          <input id="password" className="input" type="password" required minLength={8} maxLength={72}
            autoComplete="new-password" value={form.password} onChange={set('password')} />
          <p className="mt-1 text-xs text-slate-500">At least 8 characters.</p>
        </div>
        <button className="btn-primary w-full" disabled={busy}>
          {busy ? 'Creating account...' : 'Sign up'}
        </button>
        <p className="text-center text-sm text-slate-600">
          Already have an account?{' '}
          <Link to="/login" state={{ from }} className="text-indigo-600 hover:underline">Log in</Link>
        </p>
      </form>
    </div>
  )
}

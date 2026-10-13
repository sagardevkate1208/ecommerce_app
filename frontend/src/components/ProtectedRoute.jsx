import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Spinner from './Spinner'

/** Wraps pages that need a login. Remembers where the user was going so login can send them back. */
export default function ProtectedRoute() {
  const { isLoggedIn, loading } = useAuth()
  const location = useLocation()

  if (loading) return <Spinner />
  if (!isLoggedIn) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

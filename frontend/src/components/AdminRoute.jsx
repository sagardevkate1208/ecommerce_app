import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Spinner from './Spinner'

/**
 * Only lets admins through; customers are sent to the shop. This is for convenience only: the real protection
 * is on the backend, which answers 403 to any admin request from a customer token.
 */
export default function AdminRoute() {
  const { isAdmin, loading } = useAuth()
  if (loading) return <Spinner />
  return isAdmin ? <Outlet /> : <Navigate to="/" replace />
}

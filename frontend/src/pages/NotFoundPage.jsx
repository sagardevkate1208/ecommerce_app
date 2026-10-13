import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="py-20 text-center">
      <h1 className="text-4xl font-bold">404</h1>
      <p className="mt-2 text-slate-600">This page does not exist.</p>
      <Link to="/" className="btn-primary mt-6">
        Back to shop
      </Link>
    </div>
  )
}

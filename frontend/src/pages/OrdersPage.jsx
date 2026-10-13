import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../api/http'
import { orderApi } from '../api/services'
import Alert from '../components/Alert'
import Spinner from '../components/Spinner'
import StatusBadge from '../components/StatusBadge'
import { formatDate, formatMoney } from '../utils/format'

export default function OrdersPage() {
  const [orders, setOrders] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    orderApi.list().then(setOrders).catch((e) => setError(errorMessage(e)))
  }, [])

  if (error) return <Alert>{error}</Alert>
  if (!orders) return <Spinner />

  if (orders.length === 0) {
    return (
      <div className="py-20 text-center">
        <h1 className="text-2xl font-bold">No orders yet</h1>
        <Link to="/" className="btn-primary mt-6">Start shopping</Link>
      </div>
    )
  }

  return (
    <div>
      <h1 className="mb-4 text-2xl font-bold">Your orders</h1>
      <div className="space-y-3">
        {orders.map((o) => (
          <Link key={o.id} to={`/orders/${o.id}`} className="card flex items-center justify-between p-4 hover:border-indigo-300">
            <div>
              <p className="font-semibold">Order #{o.id}</p>
              <p className="text-sm text-slate-500">
                {formatDate(o.createdAt)} - {o.items.length} item(s)
              </p>
            </div>
            <div className="flex items-center gap-4">
              <StatusBadge status={o.status} />
              <span className="font-semibold">{formatMoney(o.totalAmount)}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}

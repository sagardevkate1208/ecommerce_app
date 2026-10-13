import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { errorMessage } from '../api/http'
import { orderApi, paymentApi } from '../api/services'
import Alert from '../components/Alert'
import OrderTimeline from '../components/OrderTimeline'
import PayNow from '../components/PayNow'
import Spinner from '../components/Spinner'
import StatusBadge from '../components/StatusBadge'
import { formatDate, formatMoney } from '../utils/format'

export default function OrderDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const [order, setOrder] = useState(null)
  const [payments, setPayments] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(location.state?.notice || null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const [o, allPayments] = await Promise.all([orderApi.get(id), paymentApi.list().catch(() => [])])
      setOrder(o)
      setPayments(allPayments.filter((p) => String(p.orderId) === String(id)))
    } catch (e) {
      setError(e.response?.status === 404 ? 'Order not found.' : errorMessage(e))
    }
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  const handlePayment = async (payment) => {
    setNotice(
      payment.status === 'SUCCESS'
        ? { type: 'success', text: 'Payment successful. Thank you!' }
        : null, // a failed attempt shows its own message inside the payment form
    )
    await load()
  }

  const cancel = async () => {
    if (!window.confirm('Cancel this order?')) return
    setBusy(true)
    try {
      setOrder(await orderApi.cancel(id))
      setNotice({ type: 'info', text: 'Order cancelled.' })
    } catch (e) {
      setNotice({ type: 'error', text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  if (error) {
    return (
      <div>
        <Alert>{error}</Alert>
        <Link to="/orders" className="text-indigo-600 hover:underline">Back to orders</Link>
      </div>
    )
  }
  if (!order) return <Spinner />

  const a = order.shippingAddress
  return (
    <div>
      <Link to="/orders" className="text-sm text-indigo-600 hover:underline">&larr; All orders</Link>
      <div className="mb-4 mt-2 flex items-center gap-3">
        <h1 className="text-2xl font-bold">Order #{order.id}</h1>
        <StatusBadge status={order.status} />
      </div>
      {notice && <Alert type={notice.type}>{notice.text}</Alert>}
      <p className="mb-4 text-sm text-slate-500">Placed {formatDate(order.createdAt)}</p>
      <OrderTimeline status={order.status} />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section className="card divide-y divide-slate-200">
            {order.items.map((i) => (
              <div key={i.productId} className="flex justify-between gap-4 p-4">
                <div>
                  <Link to={`/products/${i.productId}`} className="font-semibold hover:text-indigo-600">{i.productName}</Link>
                  <p className="text-sm text-slate-500">{formatMoney(i.unitPrice)} x {i.quantity}</p>
                </div>
                <p className="font-semibold">{formatMoney(i.lineTotal)}</p>
              </div>
            ))}
            <div className="flex justify-between p-4 text-lg font-bold">
              <span>Total</span>
              <span>{formatMoney(order.totalAmount)}</span>
            </div>
          </section>

          <section className="card p-5">
            <h2 className="mb-2 text-lg font-semibold">Shipping to</h2>
            <p className="text-sm">{a.recipientName}{a.phone ? ` - ${a.phone}` : ''}</p>
            <p className="text-sm text-slate-600">
              {a.line1}{a.line2 ? `, ${a.line2}` : ''}, {a.city}{a.state ? `, ${a.state}` : ''} {a.postalCode}, {a.country}
            </p>
          </section>

          {payments.length > 0 && (
            <section className="card p-5">
              <h2 className="mb-2 text-lg font-semibold">Payments</h2>
              <ul className="space-y-2 text-sm">
                {payments.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-3">
                    <span>{formatDate(p.createdAt)} - {p.method} {p.maskedReference}</span>
                    <span className="flex items-center gap-2">
                      {formatMoney(p.amount)} <StatusBadge status={p.status} />
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>

        {order.status === 'PENDING' && (
          <aside className="card h-fit space-y-4 p-5">
            <h2 className="text-lg font-semibold">Complete payment</h2>
            <PayNow order={order} onResult={handlePayment} submitLabel={`Pay ${formatMoney(order.totalAmount)}`} />
            <button className="btn-secondary w-full" onClick={cancel} disabled={busy}>
              Cancel order
            </button>
          </aside>
        )}
      </div>
    </div>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Ban, CheckCircle2, RefreshCw, Truck, Undo2 } from 'lucide-react'
import { errorMessage } from '../../api/http'
import { orderApi, paymentApi } from '../../api/services'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import StatusBadge from '../../components/StatusBadge'
import { useToast } from '../../context/ToastContext'
import { formatDate, formatMoney } from '../../utils/format'

export default function AdminOrderDetailPage() {
  const { id } = useParams()
  const toast = useToast()
  const [order, setOrder] = useState(null)
  const [payments, setPayments] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const [o, p] = await Promise.all([orderApi.adminGet(id), paymentApi.adminList({ orderId: id, size: 50 })])
      setOrder(o)
      setPayments(p.content)
    } catch (e) {
      setError(e.response?.status === 404 ? 'Order not found.' : errorMessage(e))
    }
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  const run = async (action, successText) => {
    setBusy(true)
    try {
      await action()
      toast.success(successText)
      await load()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const setStatus = (status, text) => run(() => orderApi.adminSetStatus(id, status), text)

  if (error) {
    return (
      <div>
        <Alert>{error}</Alert>
        <Link to="/admin/orders" className="text-indigo-600 hover:underline">Back to orders</Link>
      </div>
    )
  }
  if (!order) return <Spinner />

  const paid = payments.find((p) => p.status === 'SUCCESS')
  const a = order.shippingAddress

  return (
    <div>
      <Link to="/admin/orders" className="mb-2 inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline">
        <ArrowLeft className="h-4 w-4" /> All orders
      </Link>
      <div className="mb-1 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold">Order #{order.id}</h1>
        <StatusBadge status={order.status} />
      </div>
      <p className="mb-5 text-sm text-slate-500">Placed {formatDate(order.createdAt)} by user {order.userId}</p>

      {/* What can happen next depends on the status. Paid orders are cancelled through a refund. */}
      <div className="card mb-6 flex flex-wrap items-center gap-3 p-4">
        <span className="text-sm font-medium text-slate-600">Actions</span>
        {order.status === 'PENDING' && (
          <button className="btn-secondary gap-2 text-red-600" disabled={busy}
            onClick={() => window.confirm('Cancel this order?') && setStatus('CANCELLED', 'Order cancelled.')}>
            <Ban className="h-4 w-4" /> Cancel order
          </button>
        )}
        {order.status === 'PAID' && (
          <>
            <button className="btn-primary gap-2" disabled={busy} onClick={() => setStatus('SHIPPED', 'Marked as shipped.')}>
              <Truck className="h-4 w-4" /> Mark as shipped
            </button>
            {paid && (
              <button className="btn-secondary gap-2 text-red-600" disabled={busy}
                onClick={() => window.confirm('Cancel the order and refund the customer?') &&
                  run(() => paymentApi.refund(paid.id), 'Order cancelled and payment refunded.')}>
                <Undo2 className="h-4 w-4" /> Cancel and refund
              </button>
            )}
          </>
        )}
        {order.status === 'SHIPPED' && (
          <button className="btn-primary gap-2" disabled={busy} onClick={() => setStatus('DELIVERED', 'Marked as delivered.')}>
            <CheckCircle2 className="h-4 w-4" /> Mark as delivered
          </button>
        )}
        {['DELIVERED', 'CANCELLED'].includes(order.status) && (
          <span className="text-sm text-slate-500">This order is finished; nothing more to do.</span>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section className="card divide-y divide-slate-200">
            {order.items.map((i) => (
              <div key={i.productId} className="flex justify-between gap-4 p-4">
                <div>
                  <p className="font-medium">{i.productName}</p>
                  <p className="text-sm text-slate-500">SKU {i.sku} - {formatMoney(i.unitPrice)} x {i.quantity}</p>
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
            <h2 className="mb-3 font-semibold">Payments</h2>
            {payments.length === 0 ? (
              <p className="text-sm text-slate-500">No payment attempts yet.</p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {payments.map((p) => (
                  <li key={p.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                    <div>
                      <p className="font-medium">{p.method} {p.maskedReference}</p>
                      <p className="text-xs text-slate-500">{formatDate(p.createdAt)} {p.transactionRef && `- ${p.transactionRef}`}</p>
                      {p.failureReason && <p className="text-xs text-red-600">{p.failureReason}</p>}
                      {p.status === 'SUCCESS' && !p.orderSynced && (
                        <p className="text-xs text-amber-700">Paid, but the order was not updated. Use "Sync order".</p>
                      )}
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="font-semibold">{formatMoney(p.amount)}</span>
                      <StatusBadge status={p.status} />
                      {p.status === 'SUCCESS' && !p.orderSynced && (
                        <button className="btn-secondary gap-1 px-3 py-1.5" disabled={busy}
                          onClick={() => run(() => paymentApi.syncOrder(p.id), 'Order updated.')}>
                          <RefreshCw className="h-3.5 w-3.5" /> Sync order
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className="card h-fit p-5">
          <h2 className="mb-2 font-semibold">Ship to</h2>
          <p className="text-sm font-medium">{a.recipientName}</p>
          {a.phone && <p className="text-sm text-slate-600">{a.phone}</p>}
          <p className="mt-1 text-sm text-slate-600">
            {a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />
            {a.city}{a.state ? `, ${a.state}` : ''} {a.postalCode}<br />
            {a.country}
          </p>
        </aside>
      </div>
    </div>
  )
}

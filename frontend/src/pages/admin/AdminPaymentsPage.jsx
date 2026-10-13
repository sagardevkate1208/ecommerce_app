import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { RefreshCw, Undo2 } from 'lucide-react'
import { errorMessage } from '../../api/http'
import { paymentApi } from '../../api/services'
import Alert from '../../components/Alert'
import Pagination from '../../components/Pagination'
import Spinner from '../../components/Spinner'
import StatusBadge from '../../components/StatusBadge'
import PageHeader from '../../components/admin/PageHeader'
import { useToast } from '../../context/ToastContext'
import { formatDate, formatMoney } from '../../utils/format'

export default function AdminPaymentsPage() {
  const toast = useToast()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(() => {
    const params = { page, size: 10 }
    if (status) params.status = status
    return paymentApi
      .adminList(params)
      .then((r) => { setResult(r); setError('') })
      .catch((e) => setError(errorMessage(e)))
  }, [status, page])

  useEffect(() => {
    load()
  }, [load])

  const run = async (payment, action, successText) => {
    setBusyId(payment.id)
    try {
      await action()
      toast.success(successText)
      await load()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  const refund = (p) => {
    if (!window.confirm(`Refund ${formatMoney(p.amount)} for order #${p.orderId}? The order will be cancelled.`)) return
    run(p, () => paymentApi.refund(p.id), 'Payment refunded and order cancelled.')
  }

  return (
    <div>
      <PageHeader title="Payments" subtitle="All payment attempts, refunds and sync problems">
        <select className="input w-44" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All statuses</option>
          <option value="SUCCESS">Success</option>
          <option value="FAILED">Failed</option>
          <option value="REFUNDED">Refunded</option>
        </select>
      </PageHeader>

      <Alert>{error}</Alert>
      {!result ? (
        !error && <Spinner />
      ) : result.content.length === 0 ? (
        <div className="card py-12 text-center text-slate-500">No payments found.</div>
      ) : (
        <>
          <div className="card overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Payment</th>
                  <th className="px-4 py-3">Order</th>
                  <th className="px-4 py-3">Method</th>
                  <th className="px-4 py-3">Amount</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {result.content.map((p) => (
                  <tr key={p.id}>
                    <td className="px-4 py-3">
                      #{p.id}
                      <span className="block text-xs text-slate-400">{formatDate(p.createdAt)}</span>
                    </td>
                    <td className="px-4 py-3">
                      <Link to={`/admin/orders/${p.orderId}`} className="text-indigo-600 hover:underline">#{p.orderId}</Link>
                    </td>
                    <td className="px-4 py-3">{p.method}<span className="block text-xs text-slate-400">{p.maskedReference}</span></td>
                    <td className="px-4 py-3 font-medium">{formatMoney(p.amount)}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={p.status} />
                      {p.status === 'SUCCESS' && !p.orderSynced && (
                        <span className="mt-1 block text-xs text-amber-700">Order not updated</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        {p.status === 'SUCCESS' && !p.orderSynced && (
                          <button className="btn-secondary gap-1 px-3 py-1.5" disabled={busyId === p.id}
                            onClick={() => run(p, () => paymentApi.syncOrder(p.id), 'Order updated.')}>
                            <RefreshCw className="h-3.5 w-3.5" /> Sync order
                          </button>
                        )}
                        {p.status === 'SUCCESS' && (
                          <button className="btn-secondary gap-1 px-3 py-1.5 text-red-600" disabled={busyId === p.id} onClick={() => refund(p)}>
                            <Undo2 className="h-3.5 w-3.5" /> Refund
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  )
}

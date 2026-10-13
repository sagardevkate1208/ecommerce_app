import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/http'
import { orderApi } from '../../api/services'
import Alert from '../../components/Alert'
import Pagination from '../../components/Pagination'
import Spinner from '../../components/Spinner'
import StatusBadge from '../../components/StatusBadge'
import PageHeader from '../../components/admin/PageHeader'
import { formatDate, formatMoney } from '../../utils/format'

const TABS = ['ALL', 'PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED']

export default function AdminOrdersPage() {
  const [tab, setTab] = useState('ALL')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [stats, setStats] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    orderApi.adminStats().then(setStats).catch(() => setStats(null))
  }, [])

  useEffect(() => {
    setResult(null)
    const params = { page, size: 10 }
    if (tab !== 'ALL') params.status = tab
    orderApi
      .adminList(params)
      .then((r) => { setResult(r); setError('') })
      .catch((e) => setError(errorMessage(e)))
  }, [tab, page])

  const countFor = (t) => (!stats ? null : t === 'ALL' ? stats.totalOrders : stats.countByStatus[t])

  return (
    <div>
      <PageHeader title="Orders" subtitle="Track and fulfil customer orders" />

      <div className="mb-4 flex gap-1 overflow-x-auto border-b border-slate-200">
        {TABS.map((t) => (
          <button key={t} onClick={() => { setTab(t); setPage(0) }}
            className={`whitespace-nowrap border-b-2 px-4 py-2 text-sm font-medium ${
              tab === t ? 'border-indigo-600 text-indigo-600' : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}>
            {t === 'ALL' ? 'All' : t.charAt(0) + t.slice(1).toLowerCase()}
            {countFor(t) !== null && <span className="ml-1.5 rounded-full bg-slate-100 px-2 text-xs">{countFor(t)}</span>}
          </button>
        ))}
      </div>

      <Alert>{error}</Alert>
      {!result ? (
        !error && <Spinner />
      ) : result.content.length === 0 ? (
        <div className="card py-12 text-center text-slate-500">No orders here.</div>
      ) : (
        <>
          <div className="card overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Order</th>
                  <th className="px-4 py-3">Customer</th>
                  <th className="px-4 py-3">Placed</th>
                  <th className="px-4 py-3">Items</th>
                  <th className="px-4 py-3">Total</th>
                  <th className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {result.content.map((o) => (
                  <tr key={o.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3">
                      <Link to={`/admin/orders/${o.id}`} className="font-medium text-indigo-600 hover:underline">#{o.id}</Link>
                    </td>
                    <td className="px-4 py-3">{o.shippingAddress.recipientName}<span className="block text-xs text-slate-400">user {o.userId}</span></td>
                    <td className="px-4 py-3 text-slate-600">{formatDate(o.createdAt)}</td>
                    <td className="px-4 py-3">{o.items.length}</td>
                    <td className="px-4 py-3 font-medium">{formatMoney(o.totalAmount)}</td>
                    <td className="px-4 py-3"><StatusBadge status={o.status} /></td>
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

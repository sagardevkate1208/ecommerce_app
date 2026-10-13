import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ClipboardList, IndianRupee, Package, Plus, Tags } from 'lucide-react'
import { errorMessage } from '../../api/http'
import { catalogApi, orderApi } from '../../api/services'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import StatusBadge from '../../components/StatusBadge'
import PageHeader from '../../components/admin/PageHeader'
import { formatDate, formatMoney } from '../../utils/format'

// Full class names so Tailwind keeps them.
const BAR_COLOURS = {
  PENDING: 'bg-amber-400',
  PAID: 'bg-blue-500',
  SHIPPED: 'bg-indigo-500',
  DELIVERED: 'bg-green-500',
  CANCELLED: 'bg-slate-400',
}

export default function AdminDashboardPage() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    // Several small independent calls at once; the dashboard shows as soon as all are back.
    Promise.all([
      orderApi.adminStats(),
      orderApi.adminList({ size: 5 }),
      catalogApi.adminProducts({ size: 1 }),
      catalogApi.adminProducts({ size: 1, active: true }),
      catalogApi.categories(),
    ])
      .then(([stats, recent, allProducts, activeProducts, categories]) =>
        setData({
          stats,
          recent: recent.content,
          totalProducts: allProducts.totalElements,
          activeProducts: activeProducts.totalElements,
          categories: categories.length,
        }),
      )
      .catch((e) => setError(errorMessage(e)))
  }, [])

  if (error) return <Alert>{error}</Alert>
  if (!data) return <Spinner />

  const { stats, recent } = data
  const maxCount = Math.max(1, ...Object.values(stats.countByStatus))

  return (
    <div>
      <PageHeader title="Dashboard" subtitle="How the store is doing right now">
        <Link to="/admin/products/new" className="btn-primary gap-2">
          <Plus className="h-4 w-4" /> Add product
        </Link>
      </PageHeader>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard icon={IndianRupee} label="Revenue" value={formatMoney(stats.revenue)} hint="Paid, shipped and delivered orders" tone="green" />
        <StatCard icon={ClipboardList} label="Orders" value={stats.totalOrders} hint={`${stats.countByStatus.PENDING} awaiting payment`} tone="indigo" to="/admin/orders" />
        <StatCard icon={Package} label="Products" value={data.activeProducts} hint={`${data.totalProducts - data.activeProducts} hidden`} tone="blue" to="/admin/products" />
        <StatCard icon={Tags} label="Categories" value={data.categories} hint="In the catalog" tone="amber" to="/admin/categories" />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <h2 className="mb-4 font-semibold">Orders by status</h2>
          <div className="space-y-3">
            {Object.entries(stats.countByStatus).map(([status, count]) => (
              <div key={status}>
                <div className="mb-1 flex justify-between text-sm">
                  <span>{status}</span>
                  <span className="font-medium">{count}</span>
                </div>
                <div className="h-2 rounded-full bg-slate-100">
                  <div className={`h-2 rounded-full ${BAR_COLOURS[status]}`} style={{ width: `${(count / maxCount) * 100}%` }} />
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="card p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold">Recent orders</h2>
            <Link to="/admin/orders" className="text-sm text-indigo-600 hover:underline">View all</Link>
          </div>
          {recent.length === 0 ? (
            <p className="text-sm text-slate-500">No orders yet.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {recent.map((o) => (
                <li key={o.id}>
                  <Link to={`/admin/orders/${o.id}`} className="flex items-center justify-between gap-3 py-2.5 hover:bg-slate-50">
                    <div>
                      <p className="text-sm font-medium">Order #{o.id}</p>
                      <p className="text-xs text-slate-500">{formatDate(o.createdAt)}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <StatusBadge status={o.status} />
                      <span className="text-sm font-semibold">{formatMoney(o.totalAmount)}</span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

const TONES = {
  green: 'bg-green-100 text-green-700',
  indigo: 'bg-indigo-100 text-indigo-700',
  blue: 'bg-blue-100 text-blue-700',
  amber: 'bg-amber-100 text-amber-700',
}

function StatCard({ icon: Icon, label, value, hint, tone, to }) {
  const body = (
    <div className="card flex items-center gap-4 p-5 transition hover:shadow-md">
      <div className={`flex h-12 w-12 items-center justify-center rounded-full ${TONES[tone]}`}>
        <Icon className="h-6 w-6" />
      </div>
      <div>
        <p className="text-sm text-slate-500">{label}</p>
        <p className="text-2xl font-bold">{value}</p>
        <p className="text-xs text-slate-400">{hint}</p>
      </div>
    </div>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

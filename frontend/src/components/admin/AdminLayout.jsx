import { NavLink, Outlet } from 'react-router-dom'
import { ClipboardList, CreditCard, LayoutDashboard, Package, Tags } from 'lucide-react'

const LINKS = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/products', label: 'Products', icon: Package },
  { to: '/admin/categories', label: 'Categories', icon: Tags },
  { to: '/admin/orders', label: 'Orders', icon: ClipboardList },
  { to: '/admin/payments', label: 'Payments', icon: CreditCard },
]

const linkClass = ({ isActive }) =>
  `flex items-center gap-2 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium ${
    isActive ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'
  }`

/** Sidebar on large screens, a scrolling tab bar on small ones. */
export default function AdminLayout() {
  return (
    <div className="grid gap-6 lg:grid-cols-[220px_1fr]">
      <aside className="lg:sticky lg:top-20 lg:h-fit">
        <div className="card p-3">
          <p className="mb-2 hidden px-3 text-xs font-semibold uppercase tracking-wide text-slate-400 lg:block">
            Admin
          </p>
          <nav className="flex gap-1 overflow-x-auto lg:flex-col">
            {LINKS.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} className={linkClass}>
                <Icon className="h-4 w-4" />
                {label}
              </NavLink>
            ))}
          </nav>
        </div>
      </aside>
      <section className="min-w-0">
        <Outlet />
      </section>
    </div>
  )
}

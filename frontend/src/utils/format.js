const currency = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })

export const formatMoney = (value) => currency.format(Number(value ?? 0))

export const formatDate = (iso) =>
  iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : ''

/** Tailwind classes for an order or payment status badge. */
export const statusStyle = (status) =>
  ({
    PENDING: 'bg-amber-100 text-amber-800',
    PAID: 'bg-blue-100 text-blue-800',
    SHIPPED: 'bg-indigo-100 text-indigo-800',
    DELIVERED: 'bg-green-100 text-green-800',
    CANCELLED: 'bg-slate-200 text-slate-700',
    SUCCESS: 'bg-green-100 text-green-800',
    FAILED: 'bg-red-100 text-red-800',
    REFUNDED: 'bg-slate-200 text-slate-700',
  })[status] || 'bg-slate-100 text-slate-700'

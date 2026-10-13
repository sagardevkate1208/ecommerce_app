import { Ban, CheckCircle2, ClipboardCheck, CreditCard, PackageCheck, Truck } from 'lucide-react'

const STEPS = [
  { status: 'PENDING', label: 'Order placed', icon: ClipboardCheck },
  { status: 'PAID', label: 'Paid', icon: CreditCard },
  { status: 'SHIPPED', label: 'Shipped', icon: Truck },
  { status: 'DELIVERED', label: 'Delivered', icon: PackageCheck },
]

/** Progress bar of an order's journey. A cancelled order shows a notice instead. */
export default function OrderTimeline({ status }) {
  if (status === 'CANCELLED') {
    return (
      <div className="mb-4 flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-100 px-4 py-3 text-sm text-slate-700">
        <Ban className="h-4 w-4" /> This order was cancelled.
      </div>
    )
  }

  const current = STEPS.findIndex((s) => s.status === status)
  return (
    <ol className="card mb-6 flex items-start justify-between p-5">
      {STEPS.map((step, index) => {
        const done = index <= current
        const Icon = index < current ? CheckCircle2 : step.icon
        return (
          <li key={step.status} className="relative flex flex-1 flex-col items-center text-center">
            {index > 0 && (
              <span className={`absolute left-[-50%] top-5 h-0.5 w-full ${index <= current ? 'bg-indigo-600' : 'bg-slate-200'}`} />
            )}
            <span className={`relative z-10 flex h-10 w-10 items-center justify-center rounded-full ${
              done ? 'bg-indigo-600 text-white' : 'bg-slate-200 text-slate-500'
            }`}>
              <Icon className="h-5 w-5" />
            </span>
            <span className={`mt-2 text-xs font-medium ${done ? 'text-slate-900' : 'text-slate-400'}`}>{step.label}</span>
          </li>
        )
      })}
    </ol>
  )
}

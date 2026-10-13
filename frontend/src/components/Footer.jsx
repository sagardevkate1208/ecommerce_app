import { Headphones, RotateCcw, ShieldCheck, Truck } from 'lucide-react'

const PERKS = [
  { icon: Truck, title: 'Fast delivery', text: 'Tracked from our door to yours' },
  { icon: ShieldCheck, title: 'Secure payments', text: 'Your card details are never stored' },
  { icon: RotateCcw, title: 'Easy returns', text: 'Cancel a pending order any time' },
  { icon: Headphones, title: 'Friendly support', text: 'We are here to help' },
]

export default function Footer() {
  return (
    <footer className="mt-12 border-t border-slate-200 bg-white">
      <div className="mx-auto grid max-w-6xl gap-6 px-4 py-8 sm:grid-cols-2 lg:grid-cols-4">
        {PERKS.map(({ icon: Icon, title, text }) => (
          <div key={title} className="flex items-start gap-3">
            <Icon className="mt-0.5 h-6 w-6 text-indigo-600" />
            <div>
              <p className="text-sm font-semibold">{title}</p>
              <p className="text-sm text-slate-500">{text}</p>
            </div>
          </div>
        ))}
      </div>
      <p className="border-t border-slate-100 py-4 text-center text-xs text-slate-400">
        &copy; {new Date().getFullYear()} ShopEasy. Demo store: payments run in test mode.
      </p>
    </footer>
  )
}

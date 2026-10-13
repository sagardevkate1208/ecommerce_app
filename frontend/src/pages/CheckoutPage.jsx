import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/http'
import { authApi, orderApi } from '../api/services'
import Alert from '../components/Alert'
import Spinner from '../components/Spinner'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { formatMoney } from '../utils/format'

const emptyAddress = (user) => ({
  recipientName: user ? `${user.firstName} ${user.lastName}` : '',
  phone: user?.phone || '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'IN',
})

export default function CheckoutPage() {
  const { user } = useAuth()
  const { cart, refresh } = useCart()
  const navigate = useNavigate()

  const [saved, setSaved] = useState(null)
  const [address, setAddress] = useState(emptyAddress(user))
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    authApi
      .addresses()
      .then((list) => {
        setSaved(list)
        const preferred = list.find((a) => a.defaultAddress) || list[0]
        if (preferred) fillFrom(preferred)
      })
      .catch(() => setSaved([]))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const fillFrom = (a) =>
    setAddress((prev) => ({
      ...prev,
      line1: a.line1,
      line2: a.line2 || '',
      city: a.city,
      state: a.state,
      postalCode: a.postalCode,
      country: a.country,
    }))

  const set = (field) => (e) => setAddress({ ...address, [field]: e.target.value })

  if (saved === null) return <Spinner />

  if (cart.items.length === 0 && !busy) {
    return (
      <div className="py-20 text-center">
        <h1 className="text-2xl font-bold">Nothing to check out</h1>
        <Link to="/" className="btn-primary mt-6">Continue shopping</Link>
      </div>
    )
  }

  const placeOrder = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')

    // Step 1: create the order. It is saved as PENDING and the cart is emptied by the server.
    let order
    try {
      order = await orderApi.checkout({
        ...address,
        phone: address.phone || undefined,
        line2: address.line2 || undefined,
        state: address.state || undefined,
      })
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
      return
    }
    refresh()

    // Step 2 happens on the order page, which has the payment panel: pay now, or come back to it later.
    navigate(`/orders/${order.id}`, {
      replace: true,
      state: { notice: { type: 'info', text: 'Order placed. Complete your payment below to confirm it.' } },
    })
  }

  return (
    <div>
      <h1 className="mb-4 text-2xl font-bold">Checkout</h1>
      <Alert>{error}</Alert>

      <form onSubmit={placeOrder} className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section className="card p-5">
            <h2 className="mb-3 text-lg font-semibold">Shipping address</h2>

            {saved.length > 0 && (
              <div className="mb-4">
                <label className="label" htmlFor="saved">Use a saved address</label>
                <select id="saved" className="input" defaultValue=""
                  onChange={(e) => { const a = saved.find((x) => String(x.id) === e.target.value); if (a) fillFrom(a) }}>
                  <option value="">Choose...</option>
                  {saved.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.line1}, {a.city} {a.defaultAddress ? '(default)' : ''}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Recipient name" required value={address.recipientName} onChange={set('recipientName')} />
              <Field label="Phone" value={address.phone} onChange={set('phone')} />
              <div className="sm:col-span-2">
                <Field label="Address line 1" required value={address.line1} onChange={set('line1')} />
              </div>
              <div className="sm:col-span-2">
                <Field label="Address line 2" value={address.line2} onChange={set('line2')} />
              </div>
              <Field label="City" required value={address.city} onChange={set('city')} />
              <Field label="State" value={address.state} onChange={set('state')} />
              <Field label="Postal code" required value={address.postalCode} onChange={set('postalCode')} />
              <Field label="Country" required value={address.country} onChange={set('country')} />
            </div>
            {saved.length === 0 && (
              <p className="mt-3 text-xs text-slate-500">
                Tip: save addresses in your <Link to="/profile" className="text-indigo-600 hover:underline">profile</Link> to reuse them.
              </p>
            )}
          </section>

          <p className="text-sm text-slate-500">You will choose how to pay on the next step.</p>
        </div>

        <aside className="card h-fit p-5">
          <h2 className="text-lg font-semibold">Order summary</h2>
          <ul className="mt-3 space-y-2 text-sm">
            {cart.items.map((i) => (
              <li key={i.productId} className="flex justify-between gap-2">
                <span>{i.productName} x {i.quantity}</span>
                <span>{formatMoney(i.lineTotal)}</span>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex justify-between border-t border-slate-200 pt-3 text-lg font-bold">
            <span>Total</span>
            <span>{formatMoney(cart.subtotal)}</span>
          </div>
          <button className="btn-primary mt-5 w-full" disabled={busy}>
            {busy ? 'Placing order...' : `Place order (${formatMoney(cart.subtotal)})`}
          </button>
        </aside>
      </form>
    </div>
  )
}

function Field({ label, ...props }) {
  return (
    <div>
      <label className="label">{label}</label>
      <input className="input" {...props} />
    </div>
  )
}

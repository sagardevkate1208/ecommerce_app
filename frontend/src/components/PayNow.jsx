import { useEffect, useState } from 'react'
import { errorMessage } from '../api/http'
import { paymentApi } from '../api/services'
import { useAuth } from '../context/AuthContext'
import { loadRazorpay } from '../utils/razorpay'
import Alert from './Alert'
import Spinner from './Spinner'

/**
 * Pays for a PENDING order. The backend says which providers exist (GET /payments/config):
 *  - MOCK (test mode): we collect a test card / UPI id here and confirm it.
 *  - RAZORPAY (only if the backend has it switched on): Razorpay's own window collects the details, so they never
 *    touch our code, and we confirm with the proof it returns.
 * onResult(payment) is called with the payment record, whether it succeeded or was declined.
 */
export default function PayNow({ order, onResult, submitLabel = 'Pay now' }) {
  const { user } = useAuth()
  const [config, setConfig] = useState(null)
  const [provider, setProvider] = useState('')
  const [method, setMethod] = useState('CARD')
  const [reference, setReference] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    paymentApi
      .config()
      .then((c) => {
        setConfig(c)
        setProvider(c.defaultProvider)
      })
      .catch((e) => setError(errorMessage(e)))
  }, [])

  const finish = (payment) => {
    if (payment.status === 'FAILED') setError(payment.failureReason || 'Payment failed.')
    onResult?.(payment)
  }

  const payWithMock = async () => {
    const started = await paymentApi.initiate(order.id, 'MOCK')
    finish(await paymentApi.confirm(started.paymentId, { method, reference }))
  }

  const payWithRazorpay = async () => {
    const started = await paymentApi.initiate(order.id, 'RAZORPAY')
    const Razorpay = await loadRazorpay()
    // Resolves when the customer has paid and we have confirmed, or when they close the window.
    await new Promise((resolve, reject) => {
      const checkout = new Razorpay({
        key: started.keyId,
        order_id: started.gatewayOrderId,
        amount: Math.round(Number(started.amount) * 100), // Razorpay counts in paise
        currency: started.currency,
        name: 'ShopEasy',
        description: `Order #${order.id}`,
        prefill: { name: `${user.firstName} ${user.lastName}`, email: user.email, contact: user.phone || '' },
        handler: (response) => {
          paymentApi
            .confirm(started.paymentId, {
              razorpay_payment_id: response.razorpay_payment_id,
              razorpay_signature: response.razorpay_signature,
            })
            .then((payment) => { finish(payment); resolve() })
            .catch(reject)
        },
        modal: { ondismiss: () => { setError('Payment window closed. You can try again.'); resolve() } },
      })
      checkout.on('payment.failed', (r) => setError(r.error?.description || 'Payment failed.'))
      checkout.open()
    })
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (provider === 'RAZORPAY') await payWithRazorpay()
      else await payWithMock()
    } catch (err) {
      setError(err.response ? errorMessage(err) : err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!config) return error ? <Alert>{error}</Alert> : <Spinner />

  const razorpayOn = config.razorpayConfigured
  const showChoice = razorpayOn && config.mockEnabled

  return (
    <form onSubmit={submit} className="space-y-4">
      <Alert>{error}</Alert>

      {showChoice && (
        <div className="grid grid-cols-2 gap-2 text-sm">
          {[['RAZORPAY', 'Razorpay'], ['MOCK', 'Test payment']].map(([value, label]) => (
            <button type="button" key={value} onClick={() => { setProvider(value); setError('') }}
              className={`rounded-md border px-3 py-2 font-medium ${
                provider === value ? 'border-indigo-600 bg-indigo-50 text-indigo-700' : 'border-slate-300 bg-white text-slate-600'
              }`}>
              {label}
            </button>
          ))}
        </div>
      )}

      {provider === 'RAZORPAY' ? (
        <p className="rounded-md bg-slate-100 p-3 text-xs text-slate-600">
          You will pay in Razorpay's secure window. Your card details never reach this site.
        </p>
      ) : (
        <>
          <div>
            <label className="label" htmlFor="method">Payment method</label>
            <select id="method" className="input" value={method}
              onChange={(e) => { setMethod(e.target.value); setReference('') }}>
              <option value="CARD">Card</option>
              <option value="UPI">UPI</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="reference">{method === 'CARD' ? 'Card number' : 'UPI id'}</label>
            <input id="reference" className="input" required value={reference}
              placeholder={method === 'CARD' ? '4111 1111 1111 1111' : 'name@bank'}
              inputMode={method === 'CARD' ? 'numeric' : 'text'}
              onChange={(e) => setReference(e.target.value)} />
          </div>
          <p className="rounded-md bg-slate-100 p-3 text-xs text-slate-600">
            Test mode: no real money is charged. A card ending in 0002 or a UPI id starting with "fail" is declined;
            anything else succeeds.
          </p>
        </>
      )}

      <button className="btn-primary w-full" disabled={busy}>
        {busy ? 'Processing...' : submitLabel}
      </button>
    </form>
  )
}

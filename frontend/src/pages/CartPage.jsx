import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/http'
import Alert from '../components/Alert'
import ProductImage from '../components/ProductImage'
import { useCart } from '../context/CartContext'
import { formatMoney } from '../utils/format'

export default function CartPage() {
  const { cart, updateItem, removeItem, clear } = useCart()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  const run = async (productId, action) => {
    setBusyId(productId)
    setError('')
    try {
      await action()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  if (cart.items.length === 0) {
    return (
      <div className="py-20 text-center">
        <h1 className="text-2xl font-bold">Your cart is empty</h1>
        <Link to="/" className="btn-primary mt-6">
          Continue shopping
        </Link>
      </div>
    )
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Your cart</h1>
        <button className="text-sm text-red-600 hover:underline" onClick={() => run(null, clear)}>
          Clear cart
        </button>
      </div>
      <Alert>{error}</Alert>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="card divide-y divide-slate-200 lg:col-span-2">
          {cart.items.map((item) => {
            const busy = busyId === item.productId
            return (
              <div key={item.productId} className="flex gap-4 p-4">
                <ProductImage product={{ name: item.productName, imageUrl: item.imageUrl }} className="h-20 w-20 rounded-md" />
                <div className="flex-1">
                  <Link to={`/products/${item.productId}`} className="font-semibold hover:text-indigo-600">
                    {item.productName}
                  </Link>
                  <p className="text-sm text-slate-500">{formatMoney(item.unitPrice)} each</p>
                  <div className="mt-2 flex items-center gap-2">
                    <button className="btn-secondary px-3 py-1" disabled={busy || item.quantity <= 1}
                      onClick={() => run(item.productId, () => updateItem(item.productId, item.quantity - 1))}>
                      -
                    </button>
                    <span className="w-8 text-center text-sm">{item.quantity}</span>
                    <button className="btn-secondary px-3 py-1" disabled={busy || item.quantity >= 99}
                      onClick={() => run(item.productId, () => updateItem(item.productId, item.quantity + 1))}>
                      +
                    </button>
                    <button className="ml-3 text-sm text-red-600 hover:underline" disabled={busy}
                      onClick={() => run(item.productId, () => removeItem(item.productId))}>
                      Remove
                    </button>
                  </div>
                </div>
                <p className="font-semibold">{formatMoney(item.lineTotal)}</p>
              </div>
            )
          })}
        </div>

        <div className="card h-fit p-5">
          <h2 className="text-lg font-semibold">Summary</h2>
          <div className="mt-3 flex justify-between text-sm">
            <span>Items ({cart.totalItems})</span>
            <span>{formatMoney(cart.subtotal)}</span>
          </div>
          <div className="mt-3 flex justify-between border-t border-slate-200 pt-3 text-lg font-bold">
            <span>Total</span>
            <span>{formatMoney(cart.subtotal)}</span>
          </div>
          <button className="btn-primary mt-5 w-full" onClick={() => navigate('/checkout')}>
            Proceed to checkout
          </button>
          <p className="mt-2 text-xs text-slate-500">Prices are confirmed again at checkout.</p>
        </div>
      </div>
    </div>
  )
}

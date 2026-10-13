import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ShoppingCart } from 'lucide-react'
import { errorMessage } from '../api/http'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useToast } from '../context/ToastContext'

/** Adds a product to the cart. Visitors who are not logged in are sent to login first, then back here. */
export default function AddToCartButton({ productId, quantity = 1, className = 'btn-primary' }) {
  const { isLoggedIn } = useAuth()
  const { addItem } = useCart()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const [busy, setBusy] = useState(false)

  const handleClick = async () => {
    if (!isLoggedIn) {
      toast.info('Please log in to add items to your cart.')
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    setBusy(true)
    try {
      await addItem(productId, quantity)
      toast.success('Added to your cart.')
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <button className={`${className} gap-2`} onClick={handleClick} disabled={busy}>
      <ShoppingCart className="h-4 w-4" />
      {busy ? 'Adding...' : 'Add to cart'}
    </button>
  )
}

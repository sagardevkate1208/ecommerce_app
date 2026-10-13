import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { catalogApi } from '../api/services'
import { errorMessage } from '../api/http'
import AddToCartButton from '../components/AddToCartButton'
import Alert from '../components/Alert'
import ProductImage from '../components/ProductImage'
import Spinner from '../components/Spinner'
import { formatMoney } from '../utils/format'

export default function ProductDetailPage() {
  const { id } = useParams()
  const [product, setProduct] = useState(null)
  const [error, setError] = useState('')
  const [quantity, setQuantity] = useState(1)

  useEffect(() => {
    setProduct(null)
    setError('')
    catalogApi
      .product(id)
      .then(setProduct)
      .catch((e) => setError(e.response?.status === 404 ? 'This product is not available.' : errorMessage(e)))
  }, [id])

  if (error) {
    return (
      <div>
        <Alert>{error}</Alert>
        <Link to="/" className="text-indigo-600 hover:underline">
          Back to shop
        </Link>
      </div>
    )
  }
  if (!product) return <Spinner />

  return (
    <div className="card grid gap-8 p-6 md:grid-cols-2">
      <ProductImage product={product} className="h-80 w-full rounded-lg" />
      <div>
        <p className="text-xs uppercase tracking-wide text-slate-500">{product.categoryName}</p>
        <h1 className="mt-1 text-2xl font-bold">{product.name}</h1>
        <p className="mt-1 text-sm text-slate-500">SKU: {product.sku}</p>
        <p className="mt-4 text-3xl font-bold">{formatMoney(product.price)}</p>
        <p className="mt-4 whitespace-pre-line text-slate-700">{product.description}</p>

        <div className="mt-6 flex items-end gap-4">
          <div>
            <label className="label" htmlFor="qty">
              Quantity
            </label>
            <input
              id="qty"
              className="input w-24"
              type="number"
              min="1"
              max="99"
              value={quantity}
              onChange={(e) => setQuantity(Math.min(99, Math.max(1, Number(e.target.value) || 1)))}
            />
          </div>
          <AddToCartButton productId={product.id} quantity={quantity} />
        </div>
      </div>
    </div>
  )
}

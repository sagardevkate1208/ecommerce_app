import { Link } from 'react-router-dom'
import { formatMoney } from '../utils/format'
import AddToCartButton from './AddToCartButton'
import ProductImage from './ProductImage'

export default function ProductCard({ product }) {
  return (
    <div className="card group flex flex-col overflow-hidden transition hover:-translate-y-1 hover:shadow-lg">
      <Link to={`/products/${product.id}`} className="block overflow-hidden">
        <ProductImage product={product} className="h-48 w-full transition duration-300 group-hover:scale-105" />
      </Link>
      <div className="flex flex-1 flex-col p-4">
        <span className="w-fit rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">
          {product.categoryName}
        </span>
        <Link to={`/products/${product.id}`} className="mt-2 font-semibold leading-snug hover:text-indigo-600">
          {product.name}
        </Link>
        {product.description && <p className="mt-1 line-clamp-2 text-sm text-slate-500">{product.description}</p>}
        <p className="mt-auto pt-3 text-xl font-bold">{formatMoney(product.price)}</p>
        <div className="mt-3">
          <AddToCartButton productId={product.id} className="btn-primary w-full" />
        </div>
      </div>
    </div>
  )
}

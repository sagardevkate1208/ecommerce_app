import { useState } from 'react'

// Full class names (not built from pieces) so Tailwind keeps them in the build.
const GRADIENTS = [
  'from-indigo-400 to-purple-500',
  'from-rose-400 to-orange-400',
  'from-emerald-400 to-teal-500',
  'from-sky-400 to-blue-500',
  'from-amber-400 to-pink-500',
  'from-fuchsia-400 to-violet-500',
]

const gradientFor = (name = '') => {
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return GRADIENTS[hash % GRADIENTS.length]
}

/** Shows the product image, or a coloured tile with the first letter when there is no image or it fails to load. */
export default function ProductImage({ product, className = '' }) {
  // Remember WHICH url failed, so changing the url (for example in the admin form) tries again.
  const [failedUrl, setFailedUrl] = useState(null)

  if (!product.imageUrl || failedUrl === product.imageUrl) {
    return (
      <div className={`flex items-center justify-center bg-gradient-to-br ${gradientFor(product.name)} text-5xl font-bold text-white/90 ${className}`}>
        {product.name?.charAt(0).toUpperCase()}
      </div>
    )
  }
  return (
    <img
      src={product.imageUrl}
      alt={product.name}
      onError={() => setFailedUrl(product.imageUrl)}
      className={`object-cover ${className}`}
    />
  )
}

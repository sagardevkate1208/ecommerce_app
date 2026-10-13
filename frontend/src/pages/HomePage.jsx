import { useEffect, useState } from 'react'
import { Search, SlidersHorizontal } from 'lucide-react'
import { catalogApi } from '../api/services'
import { errorMessage } from '../api/http'
import Alert from '../components/Alert'
import Pagination from '../components/Pagination'
import ProductCard from '../components/ProductCard'
import ProductCardSkeleton from '../components/ProductCardSkeleton'

const SORTS = {
  newest: { label: 'Newest first', sortBy: 'createdAt', direction: 'desc' },
  priceAsc: { label: 'Price: low to high', sortBy: 'price', direction: 'asc' },
  priceDesc: { label: 'Price: high to low', sortBy: 'price', direction: 'desc' },
  name: { label: 'Name: A to Z', sortBy: 'name', direction: 'asc' },
}

const EMPTY = { q: '', categoryId: '', minPrice: '', maxPrice: '', sort: 'newest' }

export default function HomePage() {
  const [categories, setCategories] = useState([])
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [showFilters, setShowFilters] = useState(false)

  // "draft" is what is typed in the form; "filters" is what was last submitted and drives the request.
  const [draft, setDraft] = useState(EMPTY)
  const [filters, setFilters] = useState(EMPTY)
  const [page, setPage] = useState(0)

  useEffect(() => {
    catalogApi.categories().then(setCategories).catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    const { sortBy, direction } = SORTS[filters.sort]
    const params = { page, size: 12, sortBy, direction }
    if (filters.q.trim()) params.q = filters.q.trim()
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.minPrice) params.minPrice = filters.minPrice
    if (filters.maxPrice) params.maxPrice = filters.maxPrice

    setLoading(true)
    setError('')
    catalogApi
      .products(params)
      .then(setResult)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
  }, [filters, page])

  const change = (field) => (e) => setDraft({ ...draft, [field]: e.target.value })

  const apply = (e) => {
    e?.preventDefault()
    setPage(0)
    setFilters(draft)
  }

  // Clicking a category chip applies straight away.
  const pickCategory = (categoryId) => {
    const next = { ...draft, categoryId }
    setDraft(next)
    setFilters(next)
    setPage(0)
  }

  const reset = () => {
    setDraft(EMPTY)
    setFilters(EMPTY)
    setPage(0)
  }

  const filtered = filters.q || filters.categoryId || filters.minPrice || filters.maxPrice

  return (
    <div>
      <section className="mb-6 overflow-hidden rounded-2xl bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-500 px-6 py-10 text-white shadow-lg sm:px-10">
        <h1 className="text-3xl font-extrabold sm:text-4xl">Find something you will love</h1>
        <p className="mt-2 max-w-xl text-indigo-100">Browse the catalog, add to your cart and check out in a minute.</p>
        <form onSubmit={apply} className="mt-6 flex max-w-xl gap-2">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-3 h-5 w-5 text-slate-400" />
            <input
              className="w-full rounded-lg border-0 py-3 pl-10 pr-3 text-slate-800 shadow focus:outline-none focus:ring-2 focus:ring-white/60"
              placeholder="Search products"
              value={draft.q}
              onChange={change('q')}
            />
          </div>
          <button className="rounded-lg bg-slate-900 px-5 font-medium text-white hover:bg-slate-800">Search</button>
        </form>
      </section>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Chip active={!filters.categoryId} onClick={() => pickCategory('')}>All</Chip>
        {categories.map((c) => (
          <Chip key={c.id} active={String(c.id) === String(filters.categoryId)} onClick={() => pickCategory(String(c.id))}>
            {c.name}
          </Chip>
        ))}
        <button className="btn-secondary ml-auto gap-2" onClick={() => setShowFilters((v) => !v)}>
          <SlidersHorizontal className="h-4 w-4" /> Filters
        </button>
      </div>

      {showFilters && (
        <form onSubmit={apply} className="card mb-4 grid gap-3 p-4 sm:grid-cols-4">
          <input className="input" type="number" min="0" placeholder="Min price" value={draft.minPrice} onChange={change('minPrice')} />
          <input className="input" type="number" min="0" placeholder="Max price" value={draft.maxPrice} onChange={change('maxPrice')} />
          <select className="input" value={draft.sort} onChange={change('sort')}>
            {Object.entries(SORTS).map(([key, s]) => <option key={key} value={key}>{s.label}</option>)}
          </select>
          <div className="flex gap-2">
            <button className="btn-primary flex-1">Apply</button>
            <button type="button" className="btn-secondary" onClick={reset}>Reset</button>
          </div>
        </form>
      )}

      <Alert>{error}</Alert>
      {loading ? (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => <ProductCardSkeleton key={i} />)}
        </div>
      ) : result && result.content.length > 0 ? (
        <>
          <p className="mb-3 text-sm text-slate-500">{result.totalElements} product(s)</p>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {result.content.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      ) : (
        !error && (
          <div className="py-16 text-center">
            <p className="text-lg font-medium">No products found</p>
            <p className="mt-1 text-sm text-slate-500">
              {filtered ? 'Try a different search or clear the filters.' : 'The shop is empty right now. Please check back soon.'}
            </p>
            {filtered && <button className="btn-secondary mt-4" onClick={reset}>Clear filters</button>}
          </div>
        )
      )}
    </div>
  )
}

function Chip({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-4 py-1.5 text-sm font-medium transition ${
        active ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-slate-300 bg-white text-slate-700 hover:border-indigo-400'
      }`}
    >
      {children}
    </button>
  )
}

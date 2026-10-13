import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { EyeOff, Pencil, Plus, RotateCcw, Search, Trash2 } from 'lucide-react'
import { errorMessage } from '../../api/http'
import { catalogApi } from '../../api/services'
import Alert from '../../components/Alert'
import Pagination from '../../components/Pagination'
import ProductImage from '../../components/ProductImage'
import Spinner from '../../components/Spinner'
import PageHeader from '../../components/admin/PageHeader'
import { useToast } from '../../context/ToastContext'
import { formatMoney } from '../../utils/format'

const EMPTY_FILTERS = { q: '', categoryId: '', active: '' }

export default function AdminProductsPage() {
  const toast = useToast()
  const [categories, setCategories] = useState([])
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [draft, setDraft] = useState(EMPTY_FILTERS)
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    catalogApi.categories().then(setCategories).catch(() => setCategories([]))
  }, [])

  const load = useCallback(() => {
    const params = { page, size: 10 }
    if (filters.q.trim()) params.q = filters.q.trim()
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.active) params.active = filters.active
    return catalogApi
      .adminProducts(params)
      .then((r) => { setResult(r); setError('') })
      .catch((e) => setError(errorMessage(e)))
  }, [filters, page])

  useEffect(() => {
    load()
  }, [load])

  const apply = (e) => {
    e.preventDefault()
    setPage(0)
    setFilters(draft)
  }

  const act = async (product, action, successText) => {
    setBusyId(product.id)
    try {
      await action()
      toast.success(successText)
      await load()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  const remove = (p) => {
    if (!window.confirm(`Delete "${p.name}"?\n\nIt will disappear from the shop. Past orders keep it, and you can restore it later.`)) return
    act(p, () => catalogApi.deleteProduct(p.id), 'Product deleted.')
  }

  const restore = (p) =>
    act(
      p,
      () => catalogApi.updateProduct(p.id, {
        name: p.name, description: p.description, sku: p.sku, price: p.price,
        categoryId: p.categoryId, imageUrl: p.imageUrl, active: true,
      }),
      'Product restored.',
    )

  return (
    <div>
      <PageHeader title="Products" subtitle="Add, edit and remove what the shop sells">
        <Link to="/admin/products/new" className="btn-primary gap-2">
          <Plus className="h-4 w-4" /> Add product
        </Link>
      </PageHeader>

      <form onSubmit={apply} className="card mb-4 grid gap-3 p-4 md:grid-cols-4">
        <div className="relative md:col-span-2">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input className="input pl-9" placeholder="Search name, description or SKU" value={draft.q}
            onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
        </div>
        <select className="input" value={draft.categoryId} onChange={(e) => setDraft({ ...draft, categoryId: e.target.value })}>
          <option value="">All categories</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="input" value={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.value })}>
          <option value="">Active and deleted</option>
          <option value="true">Active only</option>
          <option value="false">Deleted only</option>
        </select>
        <div className="flex gap-2 md:col-span-4">
          <button className="btn-primary">Apply</button>
          <button type="button" className="btn-secondary" onClick={() => { setDraft(EMPTY_FILTERS); setFilters(EMPTY_FILTERS); setPage(0) }}>
            Reset
          </button>
        </div>
      </form>

      <Alert>{error}</Alert>
      {!result ? (
        !error && <Spinner />
      ) : result.content.length === 0 ? (
        <div className="card py-16 text-center text-slate-500">
          No products found.{' '}
          <Link to="/admin/products/new" className="text-indigo-600 hover:underline">Add the first one</Link>
        </div>
      ) : (
        <>
          <div className="card overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Product</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Price</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {result.content.map((p) => (
                  <tr key={p.id} className={p.active ? '' : 'bg-slate-50 text-slate-500'}>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <ProductImage product={p} className="h-12 w-12 rounded-md text-xl" />
                        <div>
                          <p className="font-medium">{p.name}</p>
                          <p className="text-xs text-slate-500">SKU {p.sku}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">{p.categoryName}</td>
                    <td className="px-4 py-3 font-medium">{formatMoney(p.price)}</td>
                    <td className="px-4 py-3">
                      {p.active ? (
                        <span className="rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-semibold text-green-800">Active</span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full bg-slate-200 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
                          <EyeOff className="h-3 w-3" /> Deleted
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <Link to={`/admin/products/${p.id}/edit`} className="btn-secondary gap-1 px-3 py-1.5">
                          <Pencil className="h-3.5 w-3.5" /> Edit
                        </Link>
                        {p.active ? (
                          <button className="btn-secondary gap-1 px-3 py-1.5 text-red-600" disabled={busyId === p.id} onClick={() => remove(p)}>
                            <Trash2 className="h-3.5 w-3.5" /> Delete
                          </button>
                        ) : (
                          <button className="btn-secondary gap-1 px-3 py-1.5 text-green-700" disabled={busyId === p.id} onClick={() => restore(p)}>
                            <RotateCcw className="h-3.5 w-3.5" /> Restore
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-2 text-xs text-slate-500">{result.totalElements} product(s)</p>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  )
}

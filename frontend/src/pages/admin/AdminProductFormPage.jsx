import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { errorMessage } from '../../api/http'
import { catalogApi } from '../../api/services'
import Alert from '../../components/Alert'
import ProductImage from '../../components/ProductImage'
import Spinner from '../../components/Spinner'
import PageHeader from '../../components/admin/PageHeader'
import { useToast } from '../../context/ToastContext'

const BLANK = { name: '', sku: '', price: '', categoryId: '', imageUrl: '', description: '', active: true }

/** One form for both "add" (no id in the URL) and "edit" (/admin/products/:id/edit). */
export default function AdminProductFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const toast = useToast()

  const [form, setForm] = useState(BLANK)
  const [categories, setCategories] = useState(null)
  const [loading, setLoading] = useState(isEdit)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    catalogApi.categories().then(setCategories).catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    if (!isEdit) return
    catalogApi
      .adminProduct(id)
      .then((p) =>
        setForm({
          name: p.name, sku: p.sku, price: String(p.price), categoryId: String(p.categoryId),
          imageUrl: p.imageUrl || '', description: p.description || '', active: p.active,
        }),
      )
      .catch((e) => setError(e.response?.status === 404 ? 'Product not found.' : errorMessage(e)))
      .finally(() => setLoading(false))
  }, [id, isEdit])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    const body = {
      name: form.name,
      sku: form.sku,
      price: Number(form.price),
      categoryId: Number(form.categoryId),
      imageUrl: form.imageUrl || undefined,
      description: form.description || undefined,
      active: form.active,
    }
    try {
      if (isEdit) await catalogApi.updateProduct(id, body)
      else await catalogApi.createProduct(body)
      toast.success(isEdit ? 'Product updated.' : 'Product added.')
      navigate('/admin/products')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (loading || categories === null) return <Spinner />

  return (
    <div>
      <Link to="/admin/products" className="mb-2 inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline">
        <ArrowLeft className="h-4 w-4" /> All products
      </Link>
      <PageHeader title={isEdit ? 'Edit product' : 'Add product'} />

      {categories.length === 0 ? (
        <Alert type="info">
          Products need a category.{' '}
          <Link to="/admin/categories" className="font-medium underline">Create a category first</Link>.
        </Alert>
      ) : (
        <form onSubmit={submit} className="grid gap-6 lg:grid-cols-3">
          <div className="card space-y-4 p-5 lg:col-span-2">
            <Alert>{error}</Alert>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <label className="label" htmlFor="name">Name</label>
                <input id="name" className="input" required value={form.name} onChange={set('name')} />
              </div>
              <div>
                <label className="label" htmlFor="sku">SKU</label>
                <input id="sku" className="input" required value={form.sku} onChange={set('sku')} placeholder="e.g. UB-14" />
                <p className="mt-1 text-xs text-slate-500">A unique code for this item.</p>
              </div>
              <div>
                <label className="label" htmlFor="price">Price (INR)</label>
                <input id="price" className="input" type="number" required min="0.01" step="0.01" value={form.price} onChange={set('price')} />
              </div>
              <div className="sm:col-span-2">
                <label className="label" htmlFor="category">Category</label>
                <select id="category" className="input" required value={form.categoryId} onChange={set('categoryId')}>
                  <option value="">Choose a category</option>
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div className="sm:col-span-2">
                <label className="label" htmlFor="imageUrl">Image URL</label>
                <input id="imageUrl" className="input" type="url" value={form.imageUrl} onChange={set('imageUrl')} placeholder="https://..." />
              </div>
              <div className="sm:col-span-2">
                <label className="label" htmlFor="description">Description</label>
                <textarea id="description" className="input" rows={5} maxLength={2000} value={form.description} onChange={set('description')} />
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.active} onChange={set('active')} />
              Visible in the shop
            </label>
            <div className="flex gap-2 pt-2">
              <button className="btn-primary" disabled={busy}>{busy ? 'Saving...' : isEdit ? 'Save changes' : 'Add product'}</button>
              <Link to="/admin/products" className="btn-secondary">Cancel</Link>
            </div>
          </div>

          <aside className="card h-fit p-5">
            <p className="mb-3 text-sm font-medium text-slate-600">Preview</p>
            <ProductImage product={{ name: form.name || 'New product', imageUrl: form.imageUrl }} className="h-48 w-full rounded-lg" />
            <p className="mt-3 font-semibold">{form.name || 'Product name'}</p>
            <p className="text-lg font-bold">{form.price ? `₹${Number(form.price).toLocaleString('en-IN')}` : '₹0'}</p>
          </aside>
        </form>
      )}
    </div>
  )
}

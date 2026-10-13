import { useEffect, useState } from 'react'
import { Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import { errorMessage } from '../../api/http'
import { catalogApi } from '../../api/services'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import PageHeader from '../../components/admin/PageHeader'
import { useToast } from '../../context/ToastContext'

const BLANK = { name: '', description: '' }

export default function AdminCategoriesPage() {
  const toast = useToast()
  const [categories, setCategories] = useState(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState(BLANK)
  const [editing, setEditing] = useState(null) // { id, name, description } while a row is being edited
  const [busy, setBusy] = useState(false)

  const load = () =>
    catalogApi.categories().then(setCategories).catch((e) => { setCategories([]); setError(errorMessage(e)) })

  useEffect(() => {
    load()
  }, [])

  const run = async (action, successText) => {
    setBusy(true)
    try {
      await action()
      toast.success(successText)
      await load()
      return true
    } catch (e) {
      toast.error(errorMessage(e))
      return false
    } finally {
      setBusy(false)
    }
  }

  const add = async (e) => {
    e.preventDefault()
    const ok = await run(
      () => catalogApi.createCategory({ name: form.name, description: form.description || undefined }),
      'Category added.',
    )
    if (ok) setForm(BLANK)
  }

  const save = async () => {
    const ok = await run(
      () => catalogApi.updateCategory(editing.id, { name: editing.name, description: editing.description || undefined }),
      'Category updated.',
    )
    if (ok) setEditing(null)
  }

  const remove = (c) => {
    if (!window.confirm(`Delete the category "${c.name}"?`)) return
    // The server refuses (409) while products still use it; the toast shows that message.
    run(() => catalogApi.deleteCategory(c.id), 'Category deleted.')
  }

  return (
    <div>
      <PageHeader title="Categories" subtitle="Group products so customers can find them" />
      <Alert>{error}</Alert>

      <form onSubmit={add} className="card mb-5 grid gap-3 p-4 md:grid-cols-[1fr_2fr_auto]">
        <input className="input" required placeholder="New category name" value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <input className="input" placeholder="Description (optional)" value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <button className="btn-primary gap-2" disabled={busy}>
          <Plus className="h-4 w-4" /> Add
        </button>
      </form>

      {categories === null ? (
        <Spinner />
      ) : categories.length === 0 ? (
        <div className="card py-12 text-center text-slate-500">No categories yet. Add your first one above.</div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Description</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {categories.map((c) =>
                editing?.id === c.id ? (
                  <tr key={c.id} className="bg-indigo-50/50">
                    <td className="px-4 py-2">
                      <input className="input" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
                    </td>
                    <td className="px-4 py-2">
                      <input className="input" value={editing.description || ''} onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
                    </td>
                    <td className="px-4 py-2">
                      <div className="flex justify-end gap-2">
                        <button className="btn-primary gap-1 px-3 py-1.5" onClick={save} disabled={busy || !editing.name.trim()}>
                          <Check className="h-3.5 w-3.5" /> Save
                        </button>
                        <button className="btn-secondary gap-1 px-3 py-1.5" onClick={() => setEditing(null)}>
                          <X className="h-3.5 w-3.5" /> Cancel
                        </button>
                      </div>
                    </td>
                  </tr>
                ) : (
                  <tr key={c.id}>
                    <td className="px-4 py-3 font-medium">{c.name}</td>
                    <td className="px-4 py-3 text-slate-600">{c.description || '-'}</td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <button className="btn-secondary gap-1 px-3 py-1.5" onClick={() => setEditing({ ...c })}>
                          <Pencil className="h-3.5 w-3.5" /> Edit
                        </button>
                        <button className="btn-secondary gap-1 px-3 py-1.5 text-red-600" onClick={() => remove(c)} disabled={busy}>
                          <Trash2 className="h-3.5 w-3.5" /> Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

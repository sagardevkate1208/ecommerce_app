export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null
  return (
    <div className="mt-6 flex items-center justify-center gap-3">
      <button className="btn-secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span className="text-sm text-slate-600">
        Page {page + 1} of {totalPages}
      </span>
      <button className="btn-secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next
      </button>
    </div>
  )
}

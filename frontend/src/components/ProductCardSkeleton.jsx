/** Grey placeholder shaped like a product card, shown while products load. */
export default function ProductCardSkeleton() {
  return (
    <div className="card animate-pulse overflow-hidden">
      <div className="h-48 bg-slate-200" />
      <div className="space-y-3 p-4">
        <div className="h-4 w-1/3 rounded bg-slate-200" />
        <div className="h-4 w-3/4 rounded bg-slate-200" />
        <div className="h-6 w-1/2 rounded bg-slate-200" />
        <div className="h-9 rounded bg-slate-200" />
      </div>
    </div>
  )
}

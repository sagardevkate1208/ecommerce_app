import { statusStyle } from '../utils/format'

export default function StatusBadge({ status }) {
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusStyle(status)}`}>{status}</span>
}

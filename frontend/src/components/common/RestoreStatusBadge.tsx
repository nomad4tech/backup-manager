import { StatusBadge } from './StatusBadge'
import type { RestoreRecord } from '@/api/types'

const CONFIG: Record<RestoreRecord['status'], { color: string; label: string }> = {
  PENDING:   { color: 'var(--text-muted)', label: 'Pending' },
  RUNNING:   { color: 'var(--accent)',     label: 'Running' },
  SUCCESS:   { color: 'var(--success)',    label: 'Success' },
  FAILED:    { color: 'var(--error)',      label: 'Failed' },
  CANCELLED: { color: 'var(--text-muted)', label: 'Cancelled' },
}

interface RestoreStatusBadgeProps {
  status: RestoreRecord['status']
}

export function RestoreStatusBadge({ status }: RestoreStatusBadgeProps) {
  const cfg = CONFIG[status] ?? CONFIG.FAILED
  return <StatusBadge dot={cfg.color} label={cfg.label} />
}

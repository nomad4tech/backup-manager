import { StatusBadge } from './StatusBadge'
import type { BackupStatus } from '@/api/types'

const CONFIG: Record<BackupStatus, { color: string; label: string }> = {
  RUNNING:      { color: 'var(--accent)',  label: 'Running' },
  SUCCESS:      { color: 'var(--success)', label: 'Success' },
  FAILED:       { color: 'var(--error)',   label: 'Failed' },
  UPLOADING:    { color: 'var(--accent)',  label: 'Uploading' },
  UPLOADED:     { color: 'var(--success)', label: 'Uploaded' },
  UPLOAD_FAILED:{ color: 'var(--warning)', label: 'Upload Failed' },
  SEEDED:       { color: 'var(--muted)',   label: 'Size Estimate' },
}

interface BackupStatusBadgeProps {
  status: BackupStatus
}

export function BackupStatusBadge({ status }: BackupStatusBadgeProps) {
  const cfg = CONFIG[status] ?? CONFIG.FAILED
  return <StatusBadge dot={cfg.color} label={cfg.label} />
}

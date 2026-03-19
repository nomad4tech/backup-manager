import { StatusBadge } from './StatusBadge'
import type { TaskStatus } from '@/api/types'

const CONFIG: Record<TaskStatus, { color: string; label: string }> = {
  IDLE: { color: '#6b7a99', label: 'Idle' },
  RUNNING: { color: 'var(--accent)', label: 'Running' },
  ERROR: { color: 'var(--error)', label: 'Error' },
  DISABLED: { color: 'var(--text-muted)', label: 'Disabled' },
}

interface TaskStatusBadgeProps {
  status: TaskStatus
}

export function TaskStatusBadge({ status }: TaskStatusBadgeProps) {
  const cfg = CONFIG[status] ?? CONFIG.IDLE
  return <StatusBadge dot={cfg.color} label={cfg.label} />
}

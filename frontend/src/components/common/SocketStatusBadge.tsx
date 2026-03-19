import { StatusBadge } from './StatusBadge'
import type { SocketStatus } from '@/api/types'

const CONFIG: Record<SocketStatus, { color: string; label: string }> = {
  CONNECTED: { color: 'var(--success)', label: 'Connected' },
  DISCONNECTED: { color: 'var(--text-muted)', label: 'Disconnected' },
  ERROR: { color: 'var(--error)', label: 'Error' },
}

interface SocketStatusBadgeProps {
  status: SocketStatus
}

export function SocketStatusBadge({ status }: SocketStatusBadgeProps) {
  const cfg = CONFIG[status] ?? CONFIG.DISCONNECTED
  return <StatusBadge dot={cfg.color} label={cfg.label} />
}

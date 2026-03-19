import type { BackupTask } from '@/api/types'
import { formatCronHuman, formatDelay } from '@/utils/format'

interface TaskScheduleDisplayProps {
  task: Pick<BackupTask, 'scheduleType' | 'cronExpression' | 'delayHours'>
}

export function TaskScheduleDisplay({ task }: TaskScheduleDisplayProps) {
  if (task.scheduleType === 'CRON' && task.cronExpression) {
    return (
      <div>
        <span
          className="font-mono text-xs px-1.5 py-0.5 rounded mr-2"
          style={{ backgroundColor: 'var(--bg-elevated)', color: 'var(--text-primary)' }}
        >
          {task.cronExpression}
        </span>
        <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
          {formatCronHuman(task.cronExpression)}
        </span>
      </div>
    )
  }

  if (task.scheduleType === 'DELAY' && task.delayHours != null) {
    return (
      <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>
        {formatDelay(task.delayHours * 3600)}
      </span>
    )
  }

  return <span style={{ color: 'var(--text-muted)' }}>-</span>
}

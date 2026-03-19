import { useParams, Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { useTask } from '@/hooks/useTasks'
import { useHistory } from '@/hooks/useHistory'
import { TaskStatusBadge } from '@/components/common/TaskStatusBadge'
import { TaskScheduleDisplay } from '@/components/tasks/TaskScheduleDisplay'
import { BackupHistoryTable } from '@/components/history/BackupHistoryTable'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { formatDate } from '@/utils/format'

export function TaskDetailPage() {
  const { id } = useParams<{ id: string }>()
  const taskId = Number(id)

  const { data: task, isLoading, isError, error, refetch } = useTask(taskId)
  const historyQuery = useHistory({ taskId, size: 100 })

  return (
    <div className="p-6">
      <Link
        to="/tasks"
        className="inline-flex items-center gap-1.5 text-sm mb-6 transition-colors hover:opacity-80"
        style={{ color: 'var(--text-secondary)' }}
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Tasks
      </Link>

      {isLoading && (
        <div
          className="rounded-lg border h-48 animate-pulse mb-6"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
        />
      )}

      {isError && <ErrorMessage error={error} onRetry={refetch} />}

      {task && (
        <>
          <div
            className="rounded-lg border p-6 mb-8"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
          >
            <div className="flex items-center justify-between mb-5">
              <h1 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
                {task.name}
              </h1>
              <TaskStatusBadge status={task.status} />
            </div>

            <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
              {[
                {
                  label: 'Socket / Container',
                  value: task.socketName ? `${task.socketName} · ${task.containerName}` : task.containerName,
                  mono: true,
                },
                { label: 'Database', value: task.databaseName, mono: true },
                { label: 'DB Type', value: task.databaseType },
                { label: 'Enabled', value: task.enabled ? 'Yes' : 'No' },
                {
                  label: 'Next Run',
                  value: task.nextScheduledAt ? formatDate(task.nextScheduledAt) : '-',
                },
                { label: 'Keep Backups', value: task.keepBackupsCount?.toString() ?? 'Unlimited' },
              ].map(({ label, value, mono }) => (
                <div key={label}>
                  <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-muted)' }}>
                    {label}
                  </dt>
                  <dd
                    className={`text-sm ${mono ? 'font-mono' : ''}`}
                    style={{ color: 'var(--text-primary)' }}
                  >
                    {value}
                  </dd>
                </div>
              ))}
              <div className="col-span-2">
                <dt className="text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>
                  Schedule
                </dt>
                <dd>
                  <TaskScheduleDisplay task={task} />
                </dd>
              </div>
              {task.lastError && (
                <div className="col-span-2">
                  <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--error)' }}>
                    Last Error
                  </dt>
                  <dd
                    className="text-sm font-mono p-2 rounded"
                    style={{
                      color: 'var(--error)',
                      backgroundColor: 'rgba(239,68,68,0.08)',
                    }}
                  >
                    {task.lastError}
                  </dd>
                </div>
              )}
            </dl>
          </div>

          <h2 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
            Backup History
          </h2>
          <BackupHistoryTable
            records={historyQuery.data?.content ?? []}
            isLoading={historyQuery.isLoading}
            isError={historyQuery.isError}
            error={historyQuery.error}
            onRetry={historyQuery.refetch}
          />
        </>
      )}
    </div>
  )
}

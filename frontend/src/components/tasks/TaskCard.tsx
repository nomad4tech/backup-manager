import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Play, Power, Trash2 } from 'lucide-react'
import { TaskStatusBadge } from '@/components/common/TaskStatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { TaskScheduleDisplay } from './TaskScheduleDisplay'
import { useDeleteTask, useForceRunTask, useToggleTask } from '@/hooks/useTasks'
import type { BackupTask } from '@/api/types'
import { formatDate } from '@/utils/format'

interface TaskCardProps {
  task: BackupTask
  onForceRunResult?: (result: string) => void
}

export function TaskCard({ task, onForceRunResult }: TaskCardProps) {
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [toggling, setToggling] = useState(false)
  const toggle = useToggleTask()
  const del = useDeleteTask()
  const forceRun = useForceRunTask()

  const isLoading = toggle.isPending || del.isPending || forceRun.isPending

  function handleForceRun() {
    forceRun.mutate(task.id, {
      onSuccess: (result) => onForceRunResult?.(result),
    })
  }

  return (
    <>
      <tr style={{ borderColor: 'var(--border)' }}>
        {/* Name */}
        <td className="px-4 py-3">
          <Link
            to={`/tasks/${task.id}`}
            className="text-sm font-medium hover:underline"
            style={{ color: 'var(--text-primary)' }}
          >
            {task.name}
          </Link>
          <div className="text-xs mt-0.5 font-mono" style={{ color: 'var(--text-muted)' }}>
            {task.socketName ? `${task.socketName} · ${task.containerName}` : task.containerName}
          </div>
        </td>

        {/* Database */}
        <td className="px-4 py-3">
          <span className="text-sm font-mono" style={{ color: 'var(--text-secondary)' }}>
            {task.databaseName}
          </span>
          <div className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
            {task.databaseType}
          </div>
        </td>

        {/* Schedule */}
        <td className="px-4 py-3">
          <TaskScheduleDisplay task={task} />
        </td>

        {/* Status */}
        <td className="px-4 py-3">
          <TaskStatusBadge status={task.status} />
        </td>

        {/* Next Run */}
        <td className="px-4 py-3">
          <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
            {task.nextScheduledAt ? formatDate(task.nextScheduledAt) : '-'}
          </span>
        </td>

        {/* Actions */}
        <td className="px-4 py-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleForceRun}
              disabled={isLoading || task.status === 'RUNNING'}
              title="Run now"
              className="p-1.5 rounded transition-colors disabled:opacity-40"
              style={{ color: 'var(--accent)', backgroundColor: 'rgba(59,130,246,0.1)' }}
            >
              <Play className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => {
                setToggling(true)
                toggle.mutate(task.id, { onSettled: () => setToggling(false) })
              }}
              disabled={toggling || isLoading}
              title={task.enabled ? 'Disable' : 'Enable'}
              className="p-1.5 rounded transition-colors disabled:opacity-40"
              style={{
                color: task.enabled ? 'var(--success)' : 'var(--text-muted)',
                backgroundColor: task.enabled
                  ? 'rgba(34,197,94,0.1)'
                  : 'rgba(71,85,105,0.2)',
              }}
            >
              <Power className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setConfirmDelete(true)}
              disabled={isLoading}
              title="Delete"
              className="p-1.5 rounded transition-colors disabled:opacity-40"
              style={{ color: 'var(--error)', backgroundColor: 'rgba(239,68,68,0.1)' }}
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          </div>
        </td>
      </tr>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete Task"
        description={`Are you sure you want to delete "${task.name}"?`}
        confirmLabel="Delete"
        danger
        onConfirm={() => {
          del.mutate(task.id)
          setConfirmDelete(false)
        }}
        onCancel={() => setConfirmDelete(false)}
      />
    </>
  )
}

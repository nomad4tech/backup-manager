import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, Play, Power, Pencil } from 'lucide-react'
import { useTask, useForceRunTask, useToggleTask, useUpdateTask } from '@/hooks/useTasks'
import { useHistory } from '@/hooks/useHistory'
import { TaskStatusBadge } from '@/components/common/TaskStatusBadge'
import { TaskScheduleDisplay } from '@/components/tasks/TaskScheduleDisplay'
import { BackupHistoryTable } from '@/components/history/BackupHistoryTable'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { WizardStep4Schedule } from '@/components/tasks/wizard/WizardStep4Schedule'
import { formatDate } from '@/utils/format'
import type { ScheduleType } from '@/api/types'

const inputCls =
  'w-full rounded-md border px-3 py-2 text-sm outline-none transition-colors focus:border-[var(--accent)]'
const inputSt = {
  backgroundColor: 'var(--bg-base)',
  borderColor: 'var(--border)',
  color: 'var(--text-primary)',
}
const labelSt = { color: 'var(--text-secondary)' }

const NAME_PATTERN = /^[a-z0-9_-]+$/

interface EditForm {
  name: string
  compressionEnabled: boolean
  uploadToS3: boolean
  keepBackupsCount: string
  scheduleType: ScheduleType
  cronExpression: string
  delayHours: number
}

export function TaskDetailPage() {
  const { id } = useParams<{ id: string }>()
  const taskId = Number(id)

  const { data: task, isLoading, isError, error, refetch } = useTask(taskId)
  const historyQuery = useHistory({ taskId, size: 100 })

  const forceRun = useForceRunTask()
  const toggle = useToggleTask()
  const updateTask = useUpdateTask()

  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState<EditForm | null>(null)

  function startEdit() {
    if (!task) return
    setEditForm({
      name: task.name,
      compressionEnabled: task.compressionEnabled,
      uploadToS3: task.uploadToS3,
      keepBackupsCount: task.keepBackupsCount?.toString() ?? '',
      scheduleType: task.scheduleType,
      cronExpression: task.cronExpression ?? '0 3 * * *',
      delayHours: task.delayHours ?? 1,
    })
    setEditing(true)
  }

  function cancelEdit() {
    setEditing(false)
    setEditForm(null)
  }

  function handleSave() {
    if (!task || !editForm) return
    updateTask.mutate(
      {
        id: task.id,
        data: {
          name: editForm.name,
          socketId: task.socketId,
          containerId: task.containerId,
          databaseName: task.databaseName,
          databaseType: task.databaseType,
          scheduleType: editForm.scheduleType,
          cronExpression: editForm.scheduleType === 'CRON' ? editForm.cronExpression : undefined,
          delayHours: editForm.scheduleType === 'DELAY' ? editForm.delayHours : undefined,
          keepBackupsCount: editForm.keepBackupsCount ? Number(editForm.keepBackupsCount) : undefined,
          compressionEnabled: editForm.compressionEnabled,
          uploadToS3: editForm.uploadToS3,
        },
      },
      {
        onSuccess: () => {
          setEditing(false)
          setEditForm(null)
          void refetch()
        },
      },
    )
  }

  const nameValid = editForm ? NAME_PATTERN.test(editForm.name) : true
  const nameEmpty = editForm ? editForm.name.length === 0 : false

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
            {/* Header */}
            <div className="flex items-center justify-between mb-5">
              <h1 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
                {task.name}
              </h1>

              <div className="flex items-center gap-2">
                {/* Run Now */}
                <button
                  onClick={() => forceRun.mutate(task.id, { onSuccess: () => void refetch() })}
                  disabled={task.status === 'RUNNING' || forceRun.isPending}
                  title="Run now"
                  className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors disabled:opacity-50"
                  style={{ color: 'var(--accent)', backgroundColor: 'rgba(59,130,246,0.1)' }}
                >
                  <Play className="w-3.5 h-3.5" />
                  Run Now
                </button>

                {/* Enable / Disable */}
                <button
                  onClick={() => toggle.mutate(task.id, { onSuccess: () => void refetch() })}
                  disabled={toggle.isPending}
                  title={task.enabled ? 'Disable' : 'Enable'}
                  className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors disabled:opacity-50"
                  style={{
                    color: task.enabled ? 'var(--success)' : 'var(--text-muted)',
                    backgroundColor: task.enabled
                      ? 'rgba(34,197,94,0.1)'
                      : 'rgba(71,85,105,0.2)',
                  }}
                >
                  <Power className="w-3.5 h-3.5" />
                  {task.enabled ? 'Disable' : 'Enable'}
                </button>

                {/* Edit */}
                <button
                  onClick={startEdit}
                  disabled={editing}
                  title="Edit task"
                  className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors disabled:opacity-50"
                  style={{
                    color: 'var(--text-secondary)',
                    backgroundColor: 'var(--bg-elevated)',
                  }}
                >
                  <Pencil className="w-3.5 h-3.5" />
                  Edit
                </button>
              </div>

              <TaskStatusBadge status={task.status} />
            </div>

            {/* Body: edit form or read-only grid */}
            {editing && editForm ? (
              <div className="space-y-4">
                {/* Name */}
                <div>
                  <label className="block text-xs font-medium mb-1" style={labelSt}>
                    Task Name
                  </label>
                  <input
                    className={`${inputCls} font-mono`}
                    style={{
                      ...inputSt,
                      borderColor: !nameEmpty && !nameValid ? 'var(--error)' : 'var(--border)',
                    }}
                    value={editForm.name}
                    onChange={(e) =>
                      setEditForm((f) => (f ? { ...f, name: e.target.value } : f))
                    }
                  />
                  {!nameEmpty && !nameValid && (
                    <p className="text-xs mt-1" style={{ color: 'var(--error)' }}>
                      Only lowercase letters, numbers, underscores, and hyphens allowed
                    </p>
                  )}
                </div>

                {/* Schedule + options */}
                <WizardStep4Schedule
                  config={{
                    scheduleType: editForm.scheduleType,
                    cronExpression: editForm.cronExpression,
                    delayHours: editForm.delayHours,
                    keepBackupsCount: editForm.keepBackupsCount,
                    compressionEnabled: editForm.compressionEnabled,
                    uploadToS3: editForm.uploadToS3,
                  }}
                  onChange={(cfg) =>
                    setEditForm((f) =>
                      f
                        ? {
                            ...f,
                            scheduleType: cfg.scheduleType,
                            cronExpression: cfg.cronExpression,
                            delayHours: cfg.delayHours,
                            keepBackupsCount: cfg.keepBackupsCount,
                            compressionEnabled: cfg.compressionEnabled,
                            uploadToS3: cfg.uploadToS3,
                          }
                        : f,
                    )
                  }
                />

                {/* Save / Cancel */}
                <div className="flex items-center gap-3 pt-2">
                  <button
                    onClick={handleSave}
                    disabled={updateTask.isPending || nameEmpty || !nameValid}
                    className="px-4 py-2 text-sm font-medium rounded-md transition-colors disabled:opacity-60"
                    style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
                  >
                    {updateTask.isPending ? 'Saving…' : 'Save'}
                  </button>
                  <button
                    onClick={cancelEdit}
                    disabled={updateTask.isPending}
                    className="px-4 py-2 text-sm rounded-md border transition-colors disabled:opacity-60"
                    style={{
                      borderColor: 'var(--border)',
                      color: 'var(--text-secondary)',
                      backgroundColor: 'transparent',
                    }}
                  >
                    Cancel
                  </button>
                  {updateTask.isError && (
                    <span className="text-xs" style={{ color: 'var(--error)' }}>
                      Failed to save changes
                    </span>
                  )}
                </div>
              </div>
            ) : (
              <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
                {[
                  {
                    label: 'Socket / Container',
                    value: task.socketName
                      ? `${task.socketName} · ${task.containerName}`
                      : task.containerName,
                    mono: true,
                  },
                  { label: 'Database', value: task.databaseName, mono: true },
                  { label: 'DB Type', value: task.databaseType },
                  { label: 'Enabled', value: task.enabled ? 'Yes' : 'No' },
                  {
                    label: 'Next Run',
                    value: task.nextScheduledAt ? formatDate(task.nextScheduledAt) : '-',
                  },
                  {
                    label: 'Keep Backups',
                    value: task.keepBackupsCount?.toString() ?? 'Unlimited',
                  },
                  {
                    label: 'Compression',
                    value: task.compressionEnabled ? 'Enabled' : 'Disabled',
                  },
                  {
                    label: 'Upload to S3',
                    value: task.uploadToS3 ? 'Enabled' : 'Disabled',
                  },
                ].map(({ label, value, mono }) => (
                  <div key={label}>
                    <dt
                      className="text-xs font-medium mb-0.5"
                      style={{ color: 'var(--text-muted)' }}
                    >
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
                  <dt
                    className="text-xs font-medium mb-1"
                    style={{ color: 'var(--text-muted)' }}
                  >
                    Schedule
                  </dt>
                  <dd>
                    <TaskScheduleDisplay task={task} />
                  </dd>
                </div>
                {task.lastError && (
                  <div className="col-span-2">
                    <dt
                      className="text-xs font-medium mb-0.5"
                      style={{ color: 'var(--error)' }}
                    >
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
            )}
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

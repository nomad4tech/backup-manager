import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { useTasks } from '@/hooks/useTasks'
import { TaskCard } from '@/components/tasks/TaskCard'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorMessage } from '@/components/common/ErrorMessage'

const TOAST_MESSAGES: Record<string, { text: string; type: 'success' | 'warning' | 'error' }> = {
  '202': { text: 'Backup started', type: 'success' },
  '409': { text: 'Backup already running', type: 'warning' },
  '503': { text: 'No available slots, try later', type: 'error' },
  error: { text: 'Failed to start backup', type: 'error' },
}

const TOAST_COLORS = {
  success: 'var(--success)',
  warning: 'var(--warning)',
  error: 'var(--error)',
}

export function TasksPage() {
  const { data: tasks, isLoading, isError, error, refetch } = useTasks()
  const [toast, setToast] = useState<{ text: string; type: 'success' | 'warning' | 'error' } | null>(null)
  const [search, setSearch] = useState('')

  function showToast(result: string) {
    const msg = TOAST_MESSAGES[result] ?? TOAST_MESSAGES.error
    setToast(msg)
    setTimeout(() => setToast(null), 3000)
  }

  return (
    <div className="p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
            Backup Tasks
          </h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>
            Manage scheduled backup jobs
          </p>
        </div>
        <Link
          to="/tasks/new"
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors"
          style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
        >
          <Plus className="w-4 h-4" />
          New Task
        </Link>
      </div>

      {/* Toast */}
      {toast && (
        <div
          className="fixed top-4 right-4 z-50 px-4 py-2 rounded-md text-sm font-medium shadow-lg"
          style={{ backgroundColor: TOAST_COLORS[toast.type], color: '#fff' }}
        >
          {toast.text}
        </div>
      )}

      {/* Loading skeletons */}
      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="rounded-md border h-12 animate-pulse"
              style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
            />
          ))}
        </div>
      )}

      {isError && <ErrorMessage error={error} onRetry={refetch} />}

      {!isLoading && !isError && tasks && (
        tasks.length === 0 ? (
          <EmptyState
            title="No backup tasks"
            description="Create your first backup task to get started"
            action={
              <Link
                to="/tasks/new"
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md mx-auto"
                style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
              >
                <Plus className="w-4 h-4" />
                New Task
              </Link>
            }
          />
        ) : (
          <>
            <input
              className="w-full max-w-sm rounded-md border px-3 py-2 text-sm outline-none focus:border-[var(--accent)] transition-colors mb-4"
              style={{
                backgroundColor: 'var(--bg-base)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
              placeholder="Search..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            {(() => {
              const filtered = search.length >= 3
                ? tasks.filter((t) =>
                    [t.name, t.containerName, t.databaseName, t.databaseType, t.socketName, t.status, t.scheduleType]
                      .some((f) => f?.toLowerCase().includes(search.toLowerCase())),
                  )
                : tasks
              return filtered.length === 0 ? (
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                  No results for '{search}'
                </p>
              ) : (
                <div
                  className="rounded-lg border overflow-hidden"
                  style={{ borderColor: 'var(--border)' }}
                >
                  <table className="w-full border-collapse">
                    <thead>
                      <tr
                        className="border-b text-xs uppercase tracking-wide"
                        style={{
                          backgroundColor: 'var(--bg-surface)',
                          borderColor: 'var(--border)',
                          color: 'var(--text-muted)',
                        }}
                      >
                        <th className="px-4 py-3 text-left font-medium">Name</th>
                        <th className="px-4 py-3 text-left font-medium">Database</th>
                        <th className="px-4 py-3 text-left font-medium">Schedule</th>
                        <th className="px-4 py-3 text-left font-medium">Status</th>
                        <th className="px-4 py-3 text-left font-medium">Next Run</th>
                        <th className="px-4 py-3 text-left font-medium">Actions</th>
                      </tr>
                    </thead>
                    <tbody
                      style={{ backgroundColor: 'var(--bg-base)' }}
                      className="divide-y"
                    >
                      {filtered.map((task) => (
                        <TaskCard key={task.id} task={task} onForceRunResult={showToast} />
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            })()}
          </>
        )
      )}
    </div>
  )
}

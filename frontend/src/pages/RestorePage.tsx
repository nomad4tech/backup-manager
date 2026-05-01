import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Plus, XCircle } from 'lucide-react'
import { useRestoreRecords, useCancelRestore } from '@/hooks/useRestore'
import { RestoreStatusBadge } from '@/components/common/RestoreStatusBadge'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { formatDate, formatDuration } from '@/utils/format'
import type { RestoreRecord } from '@/api/types'

const PAGE_SIZE = 20

export function RestorePage() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useRestoreRecords(page, PAGE_SIZE)
  const cancelRestore = useCancelRestore()
  const navigate = useNavigate()

  const records = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  function handleCancel(e: React.MouseEvent, record: RestoreRecord) {
    e.stopPropagation()
    cancelRestore.mutate(record.id)
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
            Restore
          </h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>
            Restore databases from backup files
          </p>
        </div>
        <Link
          to="/restore/new"
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md text-sm font-medium transition-colors"
          style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
        >
          <Plus className="w-4 h-4" />
          New Restore
        </Link>
      </div>

      {isLoading && (
        <div
          className="rounded-lg border h-48 animate-pulse"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
        />
      )}

      {isError && <ErrorMessage error={error} onRetry={refetch} />}

      {!isLoading && !isError && records.length === 0 && (
        <div
          className="rounded-lg border p-12 text-center"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
        >
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            No restore records yet.{' '}
            <Link to="/restore/new" style={{ color: 'var(--accent)' }}>
              Start a new restore
            </Link>
            .
          </p>
        </div>
      )}

      {records.length > 0 && (
        <div
          className="rounded-lg border overflow-hidden"
          style={{ borderColor: 'var(--border)' }}
        >
          <table className="w-full text-sm">
            <thead>
              <tr
                className="border-b text-xs uppercase tracking-wide"
                style={{
                  backgroundColor: 'var(--bg-elevated)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-muted)',
                }}
              >
                <th className="text-left px-4 py-3">Source DB</th>
                <th className="text-left px-4 py-3">DB Type</th>
                <th className="text-left px-4 py-3">Target DB</th>
                <th className="text-left px-4 py-3">Container</th>
                <th className="text-left px-4 py-3">Status</th>
                <th className="text-left px-4 py-3">Started</th>
                <th className="text-left px-4 py-3">Duration</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr
                  key={record.id}
                  onClick={() => navigate(`/restore/${record.id}`)}
                  className="border-b last:border-0 cursor-pointer transition-colors hover:bg-[rgba(255,255,255,0.03)]"
                  style={{
                    backgroundColor: 'var(--bg-surface)',
                    borderColor: 'var(--border)',
                  }}
                >
                  <td className="px-4 py-3 font-mono" style={{ color: 'var(--text-primary)' }}>
                    {record.sourceDatabaseName}
                  </td>
                  <td className="px-4 py-3" style={{ color: 'var(--text-secondary)' }}>
                    {record.databaseType}
                  </td>
                  <td className="px-4 py-3 font-mono" style={{ color: 'var(--text-primary)' }}>
                    {record.targetDatabaseName}
                  </td>
                  <td className="px-4 py-3 font-mono" style={{ color: 'var(--text-secondary)' }}>
                    {record.containerName}
                  </td>
                  <td className="px-4 py-3">
                    <RestoreStatusBadge status={record.status} />
                  </td>
                  <td className="px-4 py-3" style={{ color: 'var(--text-secondary)' }}>
                    {formatDate(record.startedAt)}
                  </td>
                  <td className="px-4 py-3" style={{ color: 'var(--text-secondary)' }}>
                    {record.durationMs != null ? formatDuration(record.durationMs) : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {(record.status === 'PENDING' || record.status === 'RUNNING') && (
                      <button
                        onClick={(e) => handleCancel(e, record)}
                        disabled={cancelRestore.isPending}
                        title="Cancel restore"
                        className="flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors disabled:opacity-50"
                        style={{ color: 'var(--error)', backgroundColor: 'rgba(239,68,68,0.08)' }}
                      >
                        <XCircle className="w-3.5 h-3.5" />
                        Cancel
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {totalPages > 1 && (
            <div
              className="flex items-center justify-between px-4 py-3 border-t text-xs"
              style={{ borderColor: 'var(--border)', color: 'var(--text-muted)' }}
            >
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-2 py-1 rounded border disabled:opacity-40 transition-colors"
                  style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                >
                  Previous
                </button>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-2 py-1 rounded border disabled:opacity-40 transition-colors"
                  style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

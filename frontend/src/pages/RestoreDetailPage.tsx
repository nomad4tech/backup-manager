import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, XCircle } from 'lucide-react'
import { useRestoreRecord, useCancelRestore } from '@/hooks/useRestore'
import { RestoreStatusBadge } from '@/components/common/RestoreStatusBadge'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { formatDate, formatDuration } from '@/utils/format'

export function RestoreDetailPage() {
  const { id } = useParams<{ id: string }>()
  const restoreId = Number(id)

  const { data: record, isLoading, isError, error, refetch } = useRestoreRecord(restoreId)
  const cancelRestore = useCancelRestore()

  const canCancel = record?.status === 'PENDING' || record?.status === 'RUNNING'

  return (
    <div className="p-6">
      <Link
        to="/restore"
        className="inline-flex items-center gap-1.5 text-sm mb-6 transition-colors hover:opacity-80"
        style={{ color: 'var(--text-secondary)' }}
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Restore
      </Link>

      {isLoading && (
        <div
          className="rounded-lg border h-48 animate-pulse"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
        />
      )}

      {isError && <ErrorMessage error={error} onRetry={refetch} />}

      {record && (
        <div
          className="rounded-lg border p-6"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
        >
          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
                Restore #{record.id}
              </h1>
              <p className="text-xs mt-0.5 font-mono" style={{ color: 'var(--text-muted)' }}>
                {record.sourceDatabaseName} → {record.targetDatabaseName}
              </p>
            </div>

            <div className="flex items-center gap-3">
              <RestoreStatusBadge status={record.status} />
              {canCancel && (
                <button
                  onClick={() => cancelRestore.mutate(record.id)}
                  disabled={cancelRestore.isPending}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors disabled:opacity-50"
                  style={{ color: 'var(--error)', backgroundColor: 'rgba(239,68,68,0.08)' }}
                >
                  <XCircle className="w-3.5 h-3.5" />
                  Cancel
                </button>
              )}
            </div>
          </div>

          {/* Details grid */}
          <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
            {[
              { label: 'Source Database', value: record.sourceDatabaseName, mono: true },
              { label: 'Target Database', value: record.targetDatabaseName, mono: true },
              { label: 'DB Type', value: record.databaseType },
              { label: 'Container', value: record.containerName, mono: true },
              { label: 'Started', value: formatDate(record.startedAt) },
              {
                label: 'Completed',
                value: record.completedAt ? formatDate(record.completedAt) : '—',
              },
              {
                label: 'Duration',
                value: record.durationMs != null ? formatDuration(record.durationMs) : '—',
              },
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
              <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-muted)' }}>
                Backup File
              </dt>
              <dd
                className="text-sm font-mono break-all"
                style={{ color: 'var(--text-secondary)' }}
              >
                {record.backupFilePath}
              </dd>
            </div>

            {record.errorMessage && (
              <div className="col-span-2">
                <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--error)' }}>
                  Error
                </dt>
                <dd
                  className="text-sm font-mono p-2 rounded"
                  style={{
                    color: 'var(--error)',
                    backgroundColor: 'rgba(239,68,68,0.08)',
                  }}
                >
                  {record.errorMessage}
                </dd>
              </div>
            )}
          </dl>
        </div>
      )}
    </div>
  )
}

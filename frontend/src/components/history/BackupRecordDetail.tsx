import { useEffect } from 'react'
import { X, ExternalLink } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { BackupRecord } from '@/api/types'
import { BackupStatusBadge } from '@/components/common/BackupStatusBadge'
import { formatBytes, formatDate, formatDuration } from '@/utils/format'

interface BackupRecordDetailProps {
  record: BackupRecord | null
  onClose: () => void
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
        {label}
      </dt>
      <dd className="text-sm" style={{ color: 'var(--text-primary)' }}>
        {children}
      </dd>
    </div>
  )
}

export function BackupRecordDetail({ record, onClose }: BackupRecordDetailProps) {
  useEffect(() => {
    if (!record) return
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [record, onClose])

  if (!record) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ backgroundColor: 'rgba(0,0,0,0.6)' }}
      onMouseDown={onClose}
    >
      <div
        className="rounded-lg border w-full max-w-lg shadow-xl"
        style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)' }}
        onMouseDown={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          className="flex items-start justify-between px-6 py-4 border-b"
          style={{ borderColor: 'var(--border)' }}
        >
          <div>
            <h3 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
              {record.taskName}
            </h3>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
              Record #{record.id}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {record.taskId != null && (
              <Link
                to={`/tasks/${record.taskId}`}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-md border transition-colors"
                style={{
                  borderColor: 'var(--border)',
                  color: 'var(--text-secondary)',
                  backgroundColor: 'transparent',
                }}
                onClick={onClose}
              >
                View task
                <ExternalLink className="w-3 h-3" />
              </Link>
            )}
            <button
              onClick={onClose}
              className="p-1.5 rounded-md transition-colors hover:opacity-70"
              style={{ color: 'var(--text-muted)' }}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Body */}
        <dl className="grid grid-cols-2 gap-x-6 gap-y-5 px-6 py-5">
          <Field label="Status">
            <BackupStatusBadge status={record.status} />
          </Field>

          <Field label="Database">
            <span className="font-mono">{record.databaseName}</span>
          </Field>

          <Field label="Started">
            {formatDate(record.startedAt)}
          </Field>

          <Field label="Completed">
            {record.completedAt ? formatDate(record.completedAt) : '-'}
          </Field>

          <Field label="Duration">
            {record.durationMs != null ? formatDuration(record.durationMs) : '-'}
          </Field>

          <Field label="File size">
            {record.fileSizeBytes != null ? formatBytes(record.fileSizeBytes) : '-'}
          </Field>

          {record.filePath && (
            <div className="col-span-2">
              <Field label="File path">
                <span className="font-mono text-xs break-all" style={{ color: 'var(--text-secondary)' }}>
                  {record.filePath}
                </span>
              </Field>
            </div>
          )}

          {record.awsBucketName && record.awsKey && (
            <div className="col-span-2">
              <Field label="AWS path">
                <span className="font-mono text-xs break-all" style={{ color: 'var(--text-secondary)' }}>
                  s3://{record.awsBucketName}/{record.awsKey}
                </span>
              </Field>
            </div>
          )}

          {record.containerName && (
            <div className="col-span-2">
              <Field label="Container">
                <span className="font-mono text-xs" style={{ color: 'var(--text-secondary)' }}>
                  {record.containerName}
                </span>
              </Field>
            </div>
          )}

          <div className="col-span-2">
            <Field label="Container ID">
              <span className="font-mono text-xs" style={{ color: 'var(--text-secondary)' }}>
                {record.containerId}
              </span>
            </Field>
          </div>

          {record.errorMessage && (
            <div className="col-span-2">
              <Field label="Error">
                <span
                  className="font-mono text-xs break-all"
                  style={{ color: 'var(--error)' }}
                >
                  {record.errorMessage}
                </span>
              </Field>
            </div>
          )}
        </dl>
      </div>
    </div>
  )
}

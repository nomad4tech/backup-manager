import { useState } from 'react'
import { ChevronDown, ChevronUp, ChevronsUpDown, ChevronLeft, ChevronRight, Trash2 } from 'lucide-react'
import type { BackupRecord } from '@/api/types'
import { BackupStatusBadge } from '@/components/common/BackupStatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { BackupRecordDetail } from '@/components/history/BackupRecordDetail'
import { useDeleteHistoryRecord } from '@/hooks/useHistory'
import { formatBytes, formatDate, formatDuration } from '@/utils/format'

export type SortCol = 'startedAt' | 'fileSizeBytes'
export type SortDir = 'asc' | 'desc'
export interface SortState { col: SortCol; dir: SortDir }

interface BackupHistoryTableProps {
  records: BackupRecord[]
  isLoading?: boolean
  isError?: boolean
  error?: unknown
  onRetry?: () => void
  sort?: SortState | null
  onSort?: (col: SortCol) => void
  page?: number
  pageSize?: number
  total?: number
  onPageChange?: (page: number) => void
}

function SortIcon({ col, sort }: { col: SortCol; sort?: SortState | null }) {
  if (sort?.col !== col) {
    return <ChevronsUpDown className="w-3 h-3 ml-1 opacity-40" />
  }
  return sort.dir === 'asc'
    ? <ChevronUp className="w-3 h-3 ml-1" style={{ color: 'var(--accent)' }} />
    : <ChevronDown className="w-3 h-3 ml-1" style={{ color: 'var(--accent)' }} />
}

export function BackupHistoryTable({
  records,
  isLoading,
  isError,
  error,
  onRetry,
  sort,
  onSort,
  page = 0,
  pageSize,
  total,
  onPageChange,
}: BackupHistoryTableProps) {
  const [deleteId, setDeleteId] = useState<number | null>(null)
  const [detailRecord, setDetailRecord] = useState<BackupRecord | null>(null)
  const del = useDeleteHistoryRecord()

  const deleteRecord = records.find((r) => r.id === deleteId)

  const hasPagination = onPageChange != null && total != null && pageSize != null && total > pageSize
  const totalPages = hasPagination ? Math.ceil(total! / pageSize!) : 1
  const from = hasPagination ? page * pageSize! + 1 : 1
  const to = hasPagination ? Math.min((page + 1) * pageSize!, total!) : records.length

  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <div
            key={i}
            className="h-10 rounded-md animate-pulse"
            style={{ backgroundColor: 'var(--bg-surface)' }}
          />
        ))}
      </div>
    )
  }

  if (isError) return <ErrorMessage error={error} onRetry={onRetry} />

  if (records.length === 0) {
    return (
      <EmptyState
        title="No backup records"
        description="Backups will appear here once they run"
      />
    )
  }

  function thClass(col?: SortCol) {
    return `px-4 py-3 text-left font-medium select-none ${col && onSort ? 'cursor-pointer hover:text-[var(--text-secondary)]' : ''}`
  }

  return (
    <>
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
              <th className={thClass()}>Task</th>
              <th className={thClass()}>Database</th>
              <th className={thClass()}>Status</th>
              <th
                className={thClass('startedAt')}
                onClick={() => onSort?.('startedAt')}
              >
                <span className="inline-flex items-center">
                  Started
                  {onSort && <SortIcon col="startedAt" sort={sort} />}
                </span>
              </th>
              <th className={thClass()}>Duration</th>
              <th
                className={thClass('fileSizeBytes')}
                onClick={() => onSort?.('fileSizeBytes')}
              >
                <span className="inline-flex items-center">
                  File Size
                  {onSort && <SortIcon col="fileSizeBytes" sort={sort} />}
                </span>
              </th>
              <th className={thClass()}>Actions</th>
            </tr>
          </thead>
          <tbody style={{ backgroundColor: 'var(--bg-base)' }}>
            {records.map((record) => (
              <tr
                key={record.id}
                className="border-b"
                style={{ borderColor: 'var(--border-subtle)' }}
              >
                <td className="px-4 py-3">
                  <button
                    onClick={() => setDetailRecord(record)}
                    className="text-sm text-left transition-colors hover:underline"
                    style={{ color: 'var(--accent)' }}
                  >
                    {record.taskName}
                  </button>
                  {record.containerName && (
                    <div className="text-xs font-mono mt-0.5" style={{ color: 'var(--text-muted)' }}>
                      {record.containerName}
                    </div>
                  )}
                </td>
                <td className="px-4 py-3">
                  <span className="text-sm font-mono" style={{ color: 'var(--text-secondary)' }}>
                    {record.databaseName}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <BackupStatusBadge status={record.status} />
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                    {formatDate(record.startedAt)}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                    {record.durationMs != null ? formatDuration(record.durationMs) : '-'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs font-mono" style={{ color: 'var(--text-muted)' }}>
                    {record.fileSizeBytes != null ? formatBytes(record.fileSizeBytes) : '-'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => setDeleteId(record.id)}
                    className="p-1.5 rounded transition-colors"
                    style={{ color: 'var(--error)', backgroundColor: 'rgba(239,68,68,0.1)' }}
                    title="Delete record"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {hasPagination && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
            Showing {from}–{to} of {total} results
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onPageChange!(page - 1)}
              disabled={page === 0}
              className="flex items-center gap-1 px-3 py-1.5 text-xs rounded-md border transition-colors disabled:opacity-40"
              style={{
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
                backgroundColor: 'var(--bg-elevated)',
              }}
            >
              <ChevronLeft className="w-3 h-3" />
              Previous
            </button>
            <span className="text-xs px-2" style={{ color: 'var(--text-muted)' }}>
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => onPageChange!(page + 1)}
              disabled={page >= totalPages - 1}
              className="flex items-center gap-1 px-3 py-1.5 text-xs rounded-md border transition-colors disabled:opacity-40"
              style={{
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
                backgroundColor: 'var(--bg-elevated)',
              }}
            >
              Next
              <ChevronRight className="w-3 h-3" />
            </button>
          </div>
        </div>
      )}

      <BackupRecordDetail
        record={detailRecord}
        onClose={() => setDetailRecord(null)}
      />

      <ConfirmDialog
        open={deleteId != null}
        title="Delete Backup Record"
        description={`Delete backup record for "${deleteRecord?.taskName ?? ''}"? This will also delete the backup file.`}
        confirmLabel="Delete"
        danger
        onConfirm={() => {
          if (deleteId != null) del.mutate(deleteId)
          setDeleteId(null)
        }}
        onCancel={() => setDeleteId(null)}
      />
    </>
  )
}

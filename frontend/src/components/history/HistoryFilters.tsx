import type { BackupRecord, BackupStatus } from '@/api/types'

export interface ClientFilters {
  search: string
  taskName: string
  databaseName: string
  status: string
  hasFilePath: boolean
}

export const EMPTY_CLIENT_FILTERS: ClientFilters = {
  search: '',
  taskName: '',
  databaseName: '',
  status: '',
  hasFilePath: true,
}

interface HistoryFiltersProps {
  allRecords: BackupRecord[]
  filters: ClientFilters
  onChange: (filters: ClientFilters) => void
}

const selectClass =
  'rounded-md border px-3 py-1.5 text-sm outline-none focus:border-[var(--accent)] transition-colors'
const selectStyle = {
  backgroundColor: 'var(--bg-surface)',
  borderColor: 'var(--border)',
  color: 'var(--text-primary)',
}

export function HistoryFiltersBar({ allRecords, filters, onChange }: HistoryFiltersProps) {
  const taskNames = [...new Set(allRecords.map((r) => r.taskName))].sort()
  const dbNames = [...new Set(allRecords.map((r) => r.databaseName))].sort()

  const hasActiveFilter = filters.search || filters.taskName || filters.databaseName || filters.status || !filters.hasFilePath

  function set<K extends keyof ClientFilters>(key: K, value: string) {
    onChange({ ...filters, [key]: value })
  }

  return (
    <div className="flex flex-wrap items-center gap-3 mb-5">
      {/* Text search */}
      <input
        className={selectClass}
        style={{ ...selectStyle, minWidth: '200px' }}
        value={filters.search}
        onChange={(e) => set('search', e.target.value)}
        placeholder="Search task or database…"
      />

      {/* Task */}
      <select
        className={selectClass}
        style={selectStyle}
        value={filters.taskName}
        onChange={(e) => set('taskName', e.target.value)}
      >
        <option value="">All tasks</option>
        {taskNames.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>

      {/* Database */}
      <select
        className={selectClass}
        style={selectStyle}
        value={filters.databaseName}
        onChange={(e) => set('databaseName', e.target.value)}
      >
        <option value="">All databases</option>
        {dbNames.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>

      {/* Status */}
      <select
        className={selectClass}
        style={selectStyle}
        value={filters.status}
        onChange={(e) => set('status', e.target.value as BackupStatus | '')}
      >
        <option value="">All statuses</option>
        <option value="SUCCESS">Success</option>
        <option value="UPLOADED">Uploaded</option>
        <option value="UPLOAD_FAILED">Upload Failed</option>
        <option value="FAILED">Failed</option>
        <option value="RUNNING">Running</option>
        <option value="UPLOADING">Uploading</option>
      </select>

      {/* Has file */}
      <label
        className="flex items-center gap-2 text-sm cursor-pointer select-none"
        style={{ color: 'var(--text-secondary)' }}
      >
        <input
          type="checkbox"
          checked={filters.hasFilePath}
          onChange={(e) => onChange({ ...filters, hasFilePath: e.target.checked })}
          className="w-4 h-4 rounded accent-[var(--accent)] cursor-pointer"
        />
        With file only
      </label>

      {/* Clear */}
      {hasActiveFilter && (
        <button
          onClick={() => onChange(EMPTY_CLIENT_FILTERS)}
          className="text-xs px-3 py-1.5 rounded-md border transition-colors"
          style={{
            borderColor: 'var(--border)',
            color: 'var(--text-muted)',
            backgroundColor: 'transparent',
          }}
        >
          Clear filters
        </button>
      )}
    </div>
  )
}

import { useMemo, useState } from 'react'
import { useHistory } from '@/hooks/useHistory'
import { BackupHistoryTable, type SortCol, type SortState } from '@/components/history/BackupHistoryTable'
import { HistoryFiltersBar, EMPTY_CLIENT_FILTERS, type ClientFilters } from '@/components/history/HistoryFilters'

const PAGE_SIZE = 20

export function HistoryPage() {
  // Fetch all records - client-side filtering/sorting/pagination applied below
  const query = useHistory({ size: 1000 })

  const [filters, setFilters] = useState<ClientFilters>(EMPTY_CLIENT_FILTERS)
  const [sort, setSort] = useState<SortState>({ col: 'startedAt', dir: 'desc' })
  const [page, setPage] = useState(0)

  const allRecords = query.data?.content ?? []

  function handleFilterChange(next: ClientFilters) {
    setFilters(next)
    setPage(0)
  }

  function handleSort(col: SortCol) {
    setSort((prev) => {
      if (prev.col === col) return { col, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
      // Default direction per column: newest-first for date, largest-first for size
      return { col, dir: col === 'startedAt' ? 'desc' : 'desc' }
    })
    setPage(0)
  }

  // filter → sort → paginate
  const processed = useMemo(() => {
    let result = allRecords

    if (filters.search) {
      const q = filters.search.toLowerCase()
      result = result.filter(
        (r) => r.taskName.toLowerCase().includes(q) || r.databaseName.toLowerCase().includes(q),
      )
    }
    if (filters.taskName) result = result.filter((r) => r.taskName === filters.taskName)
    if (filters.databaseName) result = result.filter((r) => r.databaseName === filters.databaseName)
    if (filters.status) result = result.filter((r) => r.status === filters.status)
    if (filters.hasFilePath) result = result.filter((r) => !!r.filePath)

    result = [...result].sort((a, b) => {
      let av: number
      let bv: number
      if (sort.col === 'startedAt') {
        av = new Date(a.startedAt).getTime()
        bv = new Date(b.startedAt).getTime()
      } else {
        av = a.fileSizeBytes ?? -1
        bv = b.fileSizeBytes ?? -1
      }
      return sort.dir === 'asc' ? av - bv : bv - av
    })

    return result
  }, [allRecords, filters, sort])

  const pageSlice = processed.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
          Backup History
        </h1>
        <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>
          View and manage backup records
        </p>
      </div>

      <HistoryFiltersBar
        allRecords={allRecords}
        filters={filters}
        onChange={handleFilterChange}
      />

      <BackupHistoryTable
        records={pageSlice}
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={query.refetch}
        sort={sort}
        onSort={handleSort}
        page={page}
        pageSize={PAGE_SIZE}
        total={processed.length}
        onPageChange={setPage}
      />
    </div>
  )
}

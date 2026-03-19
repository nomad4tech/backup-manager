import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { useDatabases } from '@/hooks/useDatabases'
import { useTasks } from '@/hooks/useTasks'

interface WizardStep3DatabaseProps {
  socketId: number
  containerId: string
  selectedDatabase: string | null
  onSelect: (db: string) => void
  onNext: () => void
}

export function WizardStep3Database({
  socketId,
  containerId,
  selectedDatabase,
  onSelect,
  onNext,
}: WizardStep3DatabaseProps) {
  const navigate = useNavigate()
  const { data: databases, isLoading, isError, error, refetch } = useDatabases(socketId, containerId)
  const { data: tasks } = useTasks()
  const [pendingTaken, setPendingTaken] = useState<string | null>(null)
  const [query, setQuery] = useState('')

  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            className="h-10 rounded-md animate-pulse"
            style={{ backgroundColor: 'var(--bg-elevated)' }}
          />
        ))}
      </div>
    )
  }

  if (isError) return <ErrorMessage error={error} onRetry={refetch} />

  if (!databases || databases.length === 0) {
    return (
      <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
        No databases found in this container.
      </p>
    )
  }

  const takenSet = new Set(
    (tasks ?? [])
      .filter((t) => t.containerId === containerId)
      .map((t) => t.databaseName),
  )

  const matchesQuery = (db: string) =>
    query.length < 3 || db.toLowerCase().includes(query.toLowerCase())

  const available = databases.filter((db) => !takenSet.has(db) && matchesQuery(db))
  const taken = databases.filter((db) => takenSet.has(db) && matchesQuery(db))

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
      <input
        className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:border-[var(--accent)] transition-colors font-mono flex-shrink-0"
        style={{
          backgroundColor: 'var(--bg-base)',
          borderColor: 'var(--border)',
          color: 'var(--text-primary)',
        }}
        placeholder="Search..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }} className="space-y-2 mt-3">
      {available.length === 0 && taken.length === 0 ? (
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
          No results for '{query}'
        </p>
      ) : (
      <>
      {available.map((db) => (
        <button
          key={db}
          onClick={() => {
            setPendingTaken(null)
            if (selectedDatabase === db) {
              onNext()
            } else {
              onSelect(db)
            }
          }}
          className="w-full flex items-center justify-between rounded-md border px-4 py-2.5 text-left transition-colors"
          style={{
            backgroundColor: selectedDatabase === db ? 'rgba(59,130,246,0.1)' : 'var(--bg-elevated)',
            borderColor: selectedDatabase === db ? 'var(--accent)' : 'var(--border)',
          }}
        >
          <span className="text-sm font-mono" style={{ color: 'var(--text-primary)' }}>
            {db}
          </span>
          {selectedDatabase === db && (
            <span className="text-xs" style={{ color: 'var(--accent)' }}>
              Click again to continue
            </span>
          )}
        </button>
      ))}

      {taken.map((db) => {
        const task = (tasks ?? []).find((t) => t.containerId === containerId && t.databaseName === db)
        const isPending = pendingTaken === db
        return (
          <button
            key={db}
            onClick={() => {
              if (isPending) {
                task && navigate(`/tasks/${task.id}`)
              } else {
                setPendingTaken(db)
              }
            }}
            className="w-full flex items-center justify-between rounded-md border px-4 py-2.5 text-left transition-colors"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              borderColor: isPending ? 'var(--warning)' : 'var(--border)',
              opacity: isPending ? 1 : 0.5,
              cursor: 'pointer',
            }}
          >
            <span className="text-sm font-mono" style={{ color: 'var(--text-muted)' }}>
              {db}
            </span>
            {isPending ? (
              <span className="text-xs" style={{ color: 'var(--warning)' }}>
                Already has task - click again to open it
              </span>
            ) : (
              <span
                className="text-xs px-1.5 py-0.5 rounded"
                style={{
                  backgroundColor: 'var(--bg-base)',
                  color: 'var(--text-muted)',
                  border: '1px solid var(--border)',
                }}
              >
                Already configured
              </span>
            )}
          </button>
        )
      })}
      </>
      )}
      </div>
    </div>
  )
}

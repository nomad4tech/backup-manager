import { useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { useContainers } from '@/hooks/useContainers'
import type { ContainerInfo, ContainerStatus } from '@/api/types'

const STATUS_COLORS: Record<ContainerStatus, string> = {
  RUNNING: 'var(--success)',
  STOPPED: 'var(--warning)',
  PAUSED: 'var(--warning)',
}

interface WizardStep2ContainerProps {
  socketId: number
  selectedContainerId: string | null
  onSelect: (container: ContainerInfo) => void
  onNext: () => void
  tasks: Array<{ containerId: string }> | undefined
}

export function WizardStep2Container({
                                       socketId,
                                       selectedContainerId,
                                       onSelect,
                                       onNext,
                                       tasks,
                                     }: WizardStep2ContainerProps) {
  const { data: containers, isLoading, isError, error, refetch } = useContainers(socketId)
  const [query, setQuery] = useState('')

  if (isLoading) {
    return (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
              <div
                  key={i}
                  className="h-14 rounded-md animate-pulse"
                  style={{ backgroundColor: 'var(--bg-elevated)' }}
              />
          ))}
        </div>
    )
  }

  if (isError) return <ErrorMessage error={error} onRetry={refetch} />

  if (!containers || containers.length === 0) {
    return (
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
          No database containers found on this socket.
        </p>
    )
  }

  const sorted = [...containers].sort((a, b) => {
    if (a.state === 'RUNNING' && b.state !== 'RUNNING') return -1
    if (a.state !== 'RUNNING' && b.state === 'RUNNING') return 1
    return 0
  })

  const filtered = query.length >= 3
    ? sorted.filter((c) =>
        [c.containerName, c.imageName, c.imageVersion, c.databaseType, c.databaseTypeName]
          .some((f) => f?.toLowerCase().includes(query.toLowerCase())),
      )
    : sorted

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
        {filtered.length === 0 ? (
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            No results for '{query}'
          </p>
        ) : filtered.map((container) => {
          const isRunning = container.state === 'RUNNING'
          const isSelected = selectedContainerId === container.containerId
          const configuredCount = (tasks ?? []).filter(t => t.containerId === container.containerId).length

          return (
              <div key={container.containerId}>
                <button
                    onClick={() => {
                      if (!isRunning) return
                      if (isSelected) {
                        onNext()
                      } else {
                        onSelect(container)
                      }
                    }}
                    disabled={!isRunning}
                    className="w-full flex items-center justify-between rounded-md border px-4 py-3 text-left transition-colors"
                    style={{
                      backgroundColor: isSelected ? 'rgba(59,130,246,0.1)' : 'var(--bg-elevated)',
                      borderColor: isSelected ? 'var(--accent)' : 'var(--border)',
                      opacity: isRunning ? 1 : 0.45,
                      cursor: isRunning ? 'pointer' : 'not-allowed',
                    }}
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium font-mono" style={{ color: 'var(--text-primary)' }}>
                      {container.containerName}
                    </p>
                    <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                      {container.imageName}
                      {container.imageVersion ? ` · ${container.imageVersion}` : ''}
                      {' · '}
                      {container.databaseTypeName ?? container.databaseType}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {isSelected && (
                      <span className="text-xs" style={{ color: 'var(--accent)' }}>
                        Click again to continue
                      </span>
                    )}
                    {configuredCount > 0 && (
                      <span
                        className="text-xs px-1.5 py-0.5 rounded"
                        style={{
                          backgroundColor: 'var(--bg-base)',
                          color: 'var(--text-muted)',
                          border: '1px solid var(--border)',
                        }}
                      >
                        {configuredCount} configured
                      </span>
                    )}
                    <div className="flex items-center gap-1.5">
                      <span
                          className="w-2 h-2 rounded-full"
                          style={{ backgroundColor: STATUS_COLORS[container.state] }}
                      />
                      <span className="text-xs uppercase" style={{ color: STATUS_COLORS[container.state] }}>
                        {container.state}
                      </span>
                    </div>
                  </div>
                </button>

                {isSelected && !isRunning && (
                    <div
                        className="flex items-center gap-2 mt-1 px-3 py-2 rounded text-xs"
                        style={{
                          backgroundColor: 'rgba(245,158,11,0.08)',
                          color: 'var(--warning)',
                        }}
                    >
                      <AlertTriangle className="w-3 h-3" />
                      Container is not running. Backups may fail.
                    </div>
                )}
              </div>
          )
        })}
        </div>
      </div>
  )
}
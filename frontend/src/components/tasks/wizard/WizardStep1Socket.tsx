import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle } from 'lucide-react'
import { SocketStatusBadge } from '@/components/common/SocketStatusBadge'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { useSockets } from '@/hooks/useSockets'
import type { DockerSocket } from '@/api/types'

interface WizardStep1SocketProps {
  selectedSocketId: number | null
  onSelect: (socket: DockerSocket) => void
  onNext: () => void
}

export function WizardStep1Socket({ selectedSocketId, onSelect, onNext }: WizardStep1SocketProps) {
  const { data: sockets, isLoading, isError, error, refetch } = useSockets()
  const [query, setQuery] = useState('')

  const connected = sockets?.filter((s) => s.status === 'CONNECTED') ?? []
  const hasConnected = connected.length > 0

  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 2 }).map((_, i) => (
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

  if (!hasConnected) {
    return (
      <div
        className="flex items-start gap-3 rounded-md border p-4"
        style={{
          backgroundColor: 'rgba(245,158,11,0.08)',
          borderColor: 'rgba(245,158,11,0.3)',
        }}
      >
        <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" style={{ color: 'var(--warning)' }} />
        <div>
          <p className="text-sm mb-2" style={{ color: 'var(--warning)' }}>
            No connected sockets. Connect a Docker socket first.
          </p>
          <Link
            to="/sockets"
            className="text-sm underline"
            style={{ color: 'var(--accent)' }}
          >
            Go to Sockets →
          </Link>
        </div>
      </div>
    )
  }

  const filtered = query.length >= 3
    ? connected.filter((s) =>
        [s.name, s.socketPath, s.sshHost, s.sshUser]
          .some((f) => f?.toLowerCase().includes(query.toLowerCase())),
      )
    : connected

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
        ) : filtered.map((socket) => (
          <button
            key={socket.id}
            onClick={() => {
              if (selectedSocketId === socket.id) {
                onNext()
              } else {
                onSelect(socket)
              }
            }}
            className="w-full flex items-center justify-between rounded-md border px-4 py-3 text-left transition-colors"
            style={{
              backgroundColor:
                selectedSocketId === socket.id ? 'rgba(59,130,246,0.1)' : 'var(--bg-elevated)',
              borderColor:
                selectedSocketId === socket.id ? 'var(--accent)' : 'var(--border)',
            }}
          >
            <div>
              <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                {socket.name}
              </p>
              <p className="text-xs font-mono mt-0.5" style={{ color: 'var(--text-muted)' }}>
                {socket.type === 'LOCAL'
                  ? socket.socketPath
                  : `${socket.sshUser}@${socket.sshHost}:${socket.sshPort ?? 22}`}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {selectedSocketId === socket.id && (
                <span className="text-xs" style={{ color: 'var(--accent)' }}>
                  Click again to continue
                </span>
              )}
              <SocketStatusBadge status={socket.status} />
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}

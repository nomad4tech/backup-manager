import { useState } from 'react'
import { Plus } from 'lucide-react'
import { useSockets, useCreateSocket } from '@/hooks/useSockets'
import { SocketCard } from '@/components/sockets/SocketCard'
import { SocketForm } from '@/components/sockets/SocketForm'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorMessage } from '@/components/common/ErrorMessage'

export function SocketsPage() {
  const [showForm, setShowForm] = useState(false)
  const { data: sockets, isLoading, isError, error, refetch } = useSockets()
  const create = useCreateSocket()

  return (
    <div className="p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
            Docker Sockets
          </h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>
            Manage Docker daemon connections
          </p>
        </div>
        <button
          onClick={() => setShowForm(true)}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors"
          style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
        >
          <Plus className="w-4 h-4" />
          Add Socket
        </button>
      </div>

      {/* Add form */}
      {showForm && (
        <div
          className="rounded-lg border p-5 mb-6"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
        >
          <h2 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
            New Socket
          </h2>
          <SocketForm
            loading={create.isPending}
            onCancel={() => setShowForm(false)}
            onSubmit={(data) =>
              create.mutate(data, { onSuccess: () => setShowForm(false) })
            }
          />
          {create.isError && (
            <div className="mt-3">
              <ErrorMessage error={create.error} />
            </div>
          )}
        </div>
      )}

      {/* Content */}
      {isLoading && (
        <div className="grid gap-4 grid-cols-1 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div
              key={i}
              className="rounded-lg border h-32 animate-pulse"
              style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
            />
          ))}
        </div>
      )}

      {isError && <ErrorMessage error={error} onRetry={refetch} />}

      {!isLoading && !isError && sockets && (
        sockets.length === 0 ? (
          <EmptyState
            title="No sockets configured"
            description="Add a Docker socket to start managing backups"
            action={
              <button
                onClick={() => setShowForm(true)}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md mx-auto"
                style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
              >
                <Plus className="w-4 h-4" />
                Add Socket
              </button>
            }
          />
        ) : (
          <div className="grid gap-4 grid-cols-1 md:grid-cols-2 xl:grid-cols-3">
            {sockets.map((socket) => (
              <SocketCard key={socket.id} socket={socket} />
            ))}
          </div>
        )
      )}
    </div>
  )
}

import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PlugZap, PlugZap2, Trash2 } from 'lucide-react'
import { SocketStatusBadge } from '@/components/common/SocketStatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { useConnectSocket, useDeleteSocket, useDisconnectSocket } from '@/hooks/useSockets'
import type { DockerSocket } from '@/api/types'
import { formatDate } from '@/utils/format'

interface SocketCardProps {
  socket: DockerSocket
}

export function SocketCard({ socket }: SocketCardProps) {
  const [confirmDelete, setConfirmDelete] = useState(false)
  const connect = useConnectSocket()
  const disconnect = useDisconnectSocket()
  const del = useDeleteSocket()

  const isConnected = socket.status === 'CONNECTED'
  const isLoading = connect.isPending || disconnect.isPending || del.isPending

  const subtitle =
    socket.type === 'LOCAL'
      ? (socket.socketPath ?? '/var/run/docker.sock')
      : `${socket.sshUser ?? ''}@${socket.sshHost ?? ''}:${socket.sshPort ?? 22}`

  return (
    <>
      <div
        className="rounded-lg border p-4 transition-colors"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border)',
        }}
      >
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="min-w-0">
            <Link
              to={`/sockets/${socket.id}`}
              className="text-sm font-semibold hover:underline"
              style={{ color: 'var(--text-primary)' }}
            >
              {socket.name}
            </Link>
            <div className="text-xs mt-0.5 font-mono" style={{ color: 'var(--text-muted)' }}>
              {subtitle}
            </div>
          </div>
          <SocketStatusBadge status={socket.status} />
        </div>

        {socket.lastConnected && (
          <p className="text-xs mb-3" style={{ color: 'var(--text-muted)' }}>
            Last connected: {formatDate(socket.lastConnected)}
          </p>
        )}

        <div className="flex items-center gap-2">
          {isConnected ? (
            <button
              onClick={() => disconnect.mutate(socket.id)}
              disabled={isLoading}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded border transition-colors disabled:opacity-50"
              style={{
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
                backgroundColor: 'var(--bg-elevated)',
              }}
            >
              <PlugZap2 className="w-3 h-3" />
              Disconnect
            </button>
          ) : (
            <button
              onClick={() => connect.mutate(socket.id)}
              disabled={isLoading}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded transition-colors disabled:opacity-50"
              style={{
                backgroundColor: 'var(--accent)',
                color: '#fff',
              }}
            >
              <PlugZap className="w-3 h-3" />
              Connect
            </button>
          )}

          {!socket.isSystem && (
            <button
              onClick={() => setConfirmDelete(true)}
              disabled={isLoading}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded border transition-colors disabled:opacity-50 ml-auto"
              style={{
                borderColor: 'rgba(239,68,68,0.3)',
                color: 'var(--error)',
                backgroundColor: 'transparent',
              }}
            >
              <Trash2 className="w-3 h-3" />
              Delete
            </button>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete Socket"
        description={`Are you sure you want to delete "${socket.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        danger
        onConfirm={() => {
          del.mutate(socket.id)
          setConfirmDelete(false)
        }}
        onCancel={() => setConfirmDelete(false)}
      />
    </>
  )
}

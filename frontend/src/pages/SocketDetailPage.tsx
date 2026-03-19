import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, PlugZap, PlugZap2 } from 'lucide-react'
import { useSockets, useConnectSocket, useDisconnectSocket } from '@/hooks/useSockets'
import { useContainers } from '@/hooks/useContainers'
import { useTasks } from '@/hooks/useTasks'
import { SocketStatusBadge } from '@/components/common/SocketStatusBadge'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { formatDate } from '@/utils/format'
import type { ContainerStatus } from '@/api/types'

const STATUS_COLORS: Record<ContainerStatus, string> = {
  RUNNING: 'var(--success)',
  STOPPED: 'var(--warning)',
  PAUSED: 'var(--warning)',
}

interface FieldProps {
  label: string
  value?: string | number
  mono?: boolean
}

function Field({ label, value, mono }: FieldProps) {
  if (value == null) return null
  return (
    <div>
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
  )
}

export function SocketDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { data: sockets, isLoading, isError, error, refetch } = useSockets()

  const socket = sockets?.find((s) => s.id === Number(id))

  const connect = useConnectSocket()
  const disconnect = useDisconnectSocket()
  const isConnected = socket?.status === 'CONNECTED'
  const actionLoading = connect.isPending || disconnect.isPending

  const { data: containers, isLoading: containersLoading } = useContainers(
    isConnected ? (socket?.id ?? 0) : 0,
  )
  const { data: tasks } = useTasks()
  const [containerQuery, setContainerQuery] = useState('')

  return (
    <div className="p-6">
      <Link
        to="/sockets"
        className="inline-flex items-center gap-1.5 text-sm mb-6 transition-colors hover:opacity-80"
        style={{ color: 'var(--text-secondary)' }}
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Sockets
      </Link>

      {isLoading && (
        <div
          className="rounded-lg border h-48 animate-pulse"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
        />
      )}

      {isError && <ErrorMessage error={error} onRetry={refetch} />}

      {!isLoading && !isError && !socket && (
        <p style={{ color: 'var(--text-muted)' }}>Socket not found.</p>
      )}

      {socket && (
        <div className="space-y-6 max-w-2xl">
          {/* Socket info card */}
          <div
            className="rounded-lg border p-6"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
          >
            <div className="flex items-center justify-between mb-6">
              <h1 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>
                {socket.name}
              </h1>
              <div className="flex items-center gap-2">
                <SocketStatusBadge status={socket.status} />
                {isConnected ? (
                  <button
                    onClick={() => disconnect.mutate(socket.id)}
                    disabled={actionLoading}
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
                    disabled={actionLoading}
                    className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded transition-colors disabled:opacity-50"
                    style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
                  >
                    <PlugZap className="w-3 h-3" />
                    Connect
                  </button>
                )}
              </div>
            </div>

            <dl className="space-y-4">
              <Field label="Type" value={socket.type} />
              {socket.type === 'LOCAL' && (
                <Field label="Socket Path" value={socket.socketPath} mono />
              )}
              {socket.type === 'REMOTE_SSH' && (
                <>
                  <Field label="Host" value={socket.sshHost} mono />
                  <Field label="Port" value={socket.sshPort} mono />
                  <Field label="User" value={socket.sshUser} />
                </>
              )}
              {socket.lastConnected && (
                <Field label="Last Connected" value={formatDate(socket.lastConnected)} />
              )}
              {socket.lastError && (
                <Field label="Last Error" value={socket.lastError} />
              )}
              {socket.socatInfo && (
                <Field label="Socat" value={socket.socatInfo} />
              )}
            </dl>
          </div>

          {/* Containers section */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                Containers
              </h2>
              {!containersLoading && containers && (
                <span
                  className="text-xs px-1.5 py-0.5 rounded-full font-medium"
                  style={{ backgroundColor: 'var(--bg-elevated)', color: 'var(--text-muted)' }}
                >
                  {containers.length}
                </span>
              )}
            </div>

            {!isConnected && (
              <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                Connect the socket to view containers and create tasks.
              </p>
            )}

            {isConnected && containersLoading && (
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, i) => (
                  <div
                    key={i}
                    className="h-14 rounded-md border animate-pulse"
                    style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)' }}
                  />
                ))}
              </div>
            )}

            {isConnected && !containersLoading && containers && containers.length === 0 && (
              <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                No database containers found on this socket.
              </p>
            )}

            {isConnected && !containersLoading && containers && containers.length > 0 && (
              <>
                <input
                  className="w-full rounded-md border px-3 py-2 text-sm font-mono outline-none focus:border-[var(--accent)] transition-colors mb-3"
                  style={{
                    backgroundColor: 'var(--bg-base)',
                    borderColor: 'var(--border)',
                    color: 'var(--text-primary)',
                  }}
                  placeholder="Search..."
                  value={containerQuery}
                  onChange={(e) => setContainerQuery(e.target.value)}
                />
                {(() => {
                  const filteredContainers = containerQuery.length >= 3
                    ? containers.filter((c) =>
                        [c.containerName, c.imageName, c.imageVersion, c.databaseType, c.databaseTypeName]
                          .some((f) => f?.toLowerCase().includes(containerQuery.toLowerCase())),
                      )
                    : containers
                  return filteredContainers.length === 0 ? (
                    <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                      No results for '{containerQuery}'
                    </p>
                  ) : (
              <div className="space-y-2">
                {filteredContainers.map((container) => {
                  const configuredCount = (tasks ?? []).filter(
                    (t) => t.containerId === container.containerId,
                  ).length

                  return (
                    <div
                      key={container.containerId}
                      className="rounded-md border px-4 py-3 flex items-center justify-between"
                      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
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
                        {container.state === 'RUNNING' && (
                          <button
                            onClick={() => navigate('/tasks/new', {
                              state: { socketId: socket.id, containerId: container.containerId },
                            })}
                            className="text-xs px-2 py-1 rounded-md transition-colors"
                            style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
                          >
                            Create task
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
                  )
                })()}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

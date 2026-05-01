import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, CheckCircle, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react'
import { useAvailableBackups, useCompatibleContainers, useCreateRestore } from '@/hooks/useRestore'
import { useSockets } from '@/hooks/useSockets'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { restoreApi } from '@/api/client'
import { formatBytes, formatDate } from '@/utils/format'
import type { BackupRecord, ContainerInfo } from '@/api/types'

const STEPS = ['Select Backup', 'Select Target', 'Confirm']

interface WizardState {
  backup: BackupRecord | null
  socketId: number | null
  container: ContainerInfo | null
  targetDatabaseName: string
}

type DbCheck = 'idle' | 'checking' | 'exists' | 'available' | 'error'

function filename(path: string): string {
  return path.split('/').pop() ?? path
}

export function RestoreWizard() {
  const navigate = useNavigate()
  const createRestore = useCreateRestore()

  const [step, setStep] = useState(0)
  const [state, setState] = useState<WizardState>({
    backup: null,
    socketId: null,
    container: null,
    targetDatabaseName: '',
  })
  const [dbCheck, setDbCheck] = useState<DbCheck>('idle')
  const [dbCheckMsg, setDbCheckMsg] = useState('')
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Trigger DB existence check with 600ms debounce
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)

    if (
      !state.socketId ||
      !state.container ||
      !state.targetDatabaseName.trim() ||
      !state.backup?.databaseType
    ) {
      setDbCheck('idle')
      return
    }

    debounceRef.current = setTimeout(async () => {
      setDbCheck('checking')
      try {
        const res = await restoreApi.checkDb(
          state.socketId!,
          state.container!.containerId,
          state.targetDatabaseName.trim(),
          state.backup!.databaseType!,
        )
        setDbCheck(res.exists ? 'exists' : 'available')
        setDbCheckMsg(res.message)
      } catch {
        setDbCheck('error')
        setDbCheckMsg('Check failed — verify the container is reachable')
      }
    }, 600)

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [state.targetDatabaseName, state.container?.containerId, state.socketId, state.backup?.databaseType])

  function canProceed(): boolean {
    switch (step) {
      case 0:
        return state.backup != null && state.backup.databaseType != null
      case 1:
        return (
          state.container != null &&
          state.targetDatabaseName.trim().length > 0 &&
          dbCheck === 'available'
        )
      case 2:
        return true
      default:
        return false
    }
  }

  function handleNext() {
    if (step < STEPS.length - 1) {
      setStep((s) => s + 1)
    } else {
      handleSubmit()
    }
  }

  function handleSubmit() {
    if (!state.backup || !state.container || !state.socketId) return
    createRestore.mutate(
      {
        backupRecordId: state.backup.id,
        socketId: state.socketId,
        containerId: state.container.containerId,
        containerName: state.container.containerName,
        targetDatabaseName: state.targetDatabaseName.trim(),
      },
      { onSuccess: () => navigate('/restore') },
    )
  }

  const isLastStep = step === STEPS.length - 1

  return (
    <div className="flex flex-col h-full max-w-2xl mx-auto">
      {/* Step indicator */}
      <div className="flex-shrink-0">
        <div className="flex items-center gap-2 mb-4">
          {STEPS.map((_label, i) => (
            <div key={i} className="flex items-center gap-2">
              <div
                className="flex items-center justify-center w-6 h-6 rounded-full text-xs font-semibold transition-colors"
                style={{
                  backgroundColor:
                    i < step ? 'var(--success)' : i === step ? 'var(--accent)' : 'var(--bg-elevated)',
                  color: i <= step ? '#fff' : 'var(--text-muted)',
                }}
              >
                {i < step ? '✓' : i + 1}
              </div>
              {i < STEPS.length - 1 && (
                <div
                  className="h-px w-6 transition-colors"
                  style={{ backgroundColor: i < step ? 'var(--success)' : 'var(--border)' }}
                />
              )}
            </div>
          ))}
        </div>
        <h2 className="text-sm font-semibold mb-0.5" style={{ color: 'var(--text-primary)' }}>
          Step {step + 1} — {STEPS[step]}
        </h2>
        <p className="text-xs mb-3" style={{ color: 'var(--text-muted)' }}>
          {step === 0 && 'Choose a backup file to restore from'}
          {step === 1 && 'Choose a target container and database name'}
          {step === 2 && 'Review and confirm the restore operation'}
        </p>
      </div>

      {/* Step content */}
      <div className="flex-1 overflow-y-auto min-h-0">
        {step === 0 && (
          <Step1Backup
            selected={state.backup}
            onSelect={(backup) =>
              setState((s) => ({
                ...s,
                backup,
                container: null,
                socketId: null,
                targetDatabaseName: backup.databaseName ?? '',
              }))
            }
          />
        )}
        {step === 1 && state.backup && (
          <Step2Target
            backup={state.backup}
            socketId={state.socketId}
            container={state.container}
            targetDatabaseName={state.targetDatabaseName}
            dbCheck={dbCheck}
            dbCheckMsg={dbCheckMsg}
            onSocketChange={(id) =>
              setState((s) => ({ ...s, socketId: id, container: null }))
            }
            onContainerSelect={(c) => setState((s) => ({ ...s, container: c }))}
            onTargetDbChange={(name) => setState((s) => ({ ...s, targetDatabaseName: name }))}
          />
        )}
        {step === 2 && state.backup && state.container && (
          <Step3Confirm
            backup={state.backup}
            container={state.container}
            targetDatabaseName={state.targetDatabaseName}
          />
        )}
      </div>

      {/* Navigation */}
      <div
        className="flex-shrink-0 py-3"
        style={{ borderTop: '1px solid var(--border)', backgroundColor: 'var(--bg-base)' }}
      >
        {createRestore.isError && (
          <div className="mb-3">
            <ErrorMessage error={createRestore.error} />
          </div>
        )}
        <div className="flex items-center justify-between">
          <button
            onClick={() => setStep((s) => s - 1)}
            disabled={step === 0}
            className="flex items-center gap-1.5 px-4 py-2 text-sm rounded-md border transition-colors disabled:opacity-40"
            style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
          >
            <ChevronLeft className="w-4 h-4" />
            Back
          </button>
          <button
            onClick={handleNext}
            disabled={!canProceed() || createRestore.isPending}
            className="flex items-center gap-1.5 px-5 py-2 text-sm font-medium rounded-md transition-colors disabled:opacity-40"
            style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
          >
            {createRestore.isPending ? 'Starting...' : isLastStep ? 'Start Restore' : 'Next'}
            {!isLastStep && <ChevronRight className="w-4 h-4" />}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Step 1: Select Backup ────────────────────────────────────────────────────

function Step1Backup({
  selected,
  onSelect,
}: {
  selected: BackupRecord | null
  onSelect: (b: BackupRecord) => void
}) {
  const { data: backups, isLoading, isError, error, refetch } = useAvailableBackups()

  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-20 rounded-md animate-pulse" style={{ backgroundColor: 'var(--bg-elevated)' }} />
        ))}
      </div>
    )
  }
  if (isError) return <ErrorMessage error={error} onRetry={refetch} />
  if (!backups?.length) {
    return (
      <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
        No backups with files on disk found.
      </p>
    )
  }

  return (
    <div className="space-y-2">
      {backups.map((b) => {
        const noType = !b.databaseType
        const isSelected = selected?.id === b.id
        return (
          <button
            key={b.id}
            disabled={noType}
            onClick={() => onSelect(b)}
            className="w-full text-left rounded-md border px-4 py-3 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            style={{
              backgroundColor: isSelected ? 'rgba(59,130,246,0.1)' : 'var(--bg-elevated)',
              borderColor: isSelected ? 'var(--accent)' : 'var(--border)',
            }}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-mono truncate" style={{ color: 'var(--text-primary)' }}>
                  {filename(b.filePath ?? b.id.toString())}
                </p>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  {b.databaseName}
                  {b.fileSizeBytes != null && ` · ${formatBytes(b.fileSizeBytes)}`}
                  {` · ${formatDate(b.startedAt)}`}
                </p>
              </div>
              <div className="flex items-center gap-2 flex-shrink-0">
                {noType ? (
                  <span
                    className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded"
                    style={{ backgroundColor: 'rgba(239,68,68,0.1)', color: 'var(--error)' }}
                  >
                    <AlertTriangle className="w-3 h-3" />
                    No DB type
                  </span>
                ) : (
                  <span
                    className="text-xs px-2 py-0.5 rounded font-medium"
                    style={{ backgroundColor: 'var(--bg-surface)', color: 'var(--text-secondary)' }}
                  >
                    {b.databaseType}
                  </span>
                )}
              </div>
            </div>
          </button>
        )
      })}
    </div>
  )
}

// ─── Step 2: Select Target ────────────────────────────────────────────────────

function Step2Target({
  backup,
  socketId,
  container,
  targetDatabaseName,
  dbCheck,
  dbCheckMsg,
  onSocketChange,
  onContainerSelect,
  onTargetDbChange,
}: {
  backup: BackupRecord
  socketId: number | null
  container: ContainerInfo | null
  targetDatabaseName: string
  dbCheck: DbCheck
  dbCheckMsg: string
  onSocketChange: (id: number) => void
  onContainerSelect: (c: ContainerInfo) => void
  onTargetDbChange: (name: string) => void
}) {
  const { data: sockets } = useSockets()
  const connected = sockets?.filter((s) => s.status === 'CONNECTED') ?? []

  const { data: containers, isLoading: loadingContainers } = useCompatibleContainers(
    socketId,
    backup.databaseType ?? null,
  )

  return (
    <div className="space-y-5">
      {/* Socket selector */}
      <div>
        <label className="block text-xs font-medium mb-1.5" style={{ color: 'var(--text-secondary)' }}>
          Docker Socket
        </label>
        {connected.length === 0 ? (
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>No connected sockets available.</p>
        ) : (
          <select
            className="w-full rounded-md border px-3 py-2 text-sm outline-none transition-colors focus:border-[var(--accent)]"
            style={{
              backgroundColor: 'var(--bg-base)',
              borderColor: 'var(--border)',
              color: 'var(--text-primary)',
            }}
            value={socketId ?? ''}
            onChange={(e) => onSocketChange(Number(e.target.value))}
          >
            <option value="">— Select a socket —</option>
            {connected.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        )}
      </div>

      {/* Container list */}
      {socketId != null && (
        <div>
          <label className="block text-xs font-medium mb-1.5" style={{ color: 'var(--text-secondary)' }}>
            Target Container ({backup.databaseType})
          </label>
          {loadingContainers ? (
            <div className="space-y-2">
              {Array.from({ length: 2 }).map((_, i) => (
                <div key={i} className="h-14 rounded-md animate-pulse" style={{ backgroundColor: 'var(--bg-elevated)' }} />
              ))}
            </div>
          ) : !containers?.length ? (
            <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
              No compatible containers found on this socket.
            </p>
          ) : (
            <div className="space-y-2">
              {containers.map((c) => {
                const running = c.state === 'RUNNING'
                const isSelected = container?.containerId === c.containerId
                return (
                  <button
                    key={c.containerId}
                    disabled={!running}
                    onClick={() => onContainerSelect(c)}
                    title={running ? undefined : 'Container is not running'}
                    className="w-full text-left rounded-md border px-4 py-3 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                    style={{
                      backgroundColor: isSelected ? 'rgba(59,130,246,0.1)' : 'var(--bg-elevated)',
                      borderColor: isSelected ? 'var(--accent)' : 'var(--border)',
                    }}
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium font-mono" style={{ color: 'var(--text-primary)' }}>
                          {c.containerName}
                        </p>
                        <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                          {c.imageName}
                        </p>
                      </div>
                      <span
                        className="text-xs font-medium"
                        style={{ color: running ? 'var(--success)' : 'var(--text-muted)' }}
                      >
                        {c.state}
                      </span>
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* Target DB name */}
      {container && (
        <div>
          <label className="block text-xs font-medium mb-1.5" style={{ color: 'var(--text-secondary)' }}>
            Target Database Name
          </label>
          <input
            className="w-full rounded-md border px-3 py-2 text-sm font-mono outline-none transition-colors focus:border-[var(--accent)]"
            style={{
              backgroundColor: 'var(--bg-base)',
              borderColor: dbCheck === 'exists' ? 'var(--error)' : 'var(--border)',
              color: 'var(--text-primary)',
            }}
            value={targetDatabaseName}
            onChange={(e) => onTargetDbChange(e.target.value)}
            placeholder="database_name"
          />

          {/* DB check feedback */}
          <div className="mt-1.5 min-h-[18px]">
            {dbCheck === 'checking' && (
              <span className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--text-muted)' }}>
                <Loader2 className="w-3 h-3 animate-spin" />
                Checking...
              </span>
            )}
            {dbCheck === 'exists' && (
              <p className="text-xs" style={{ color: 'var(--error)' }}>
                ⚠ Database &apos;{targetDatabaseName}&apos; already exists in this container. You must delete it
                manually before restoring.
              </p>
            )}
            {dbCheck === 'available' && (
              <span className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--success)' }}>
                <CheckCircle className="w-3 h-3" />
                Database name is available
              </span>
            )}
            {dbCheck === 'error' && (
              <p className="text-xs" style={{ color: 'var(--error)' }}>
                {dbCheckMsg}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Step 3: Confirm ──────────────────────────────────────────────────────────

function Step3Confirm({
  backup,
  container,
  targetDatabaseName,
}: {
  backup: BackupRecord
  container: ContainerInfo
  targetDatabaseName: string
}) {
  const rows = [
    { label: 'Backup file', value: filename(backup.filePath ?? backup.id.toString()) },
    { label: 'Source database', value: backup.databaseName },
    { label: 'DB type', value: backup.databaseType ?? '—' },
    { label: 'Target container', value: container.containerName },
    { label: 'Target database', value: targetDatabaseName },
  ]

  return (
    <div className="space-y-4">
      <div
        className="rounded-lg border p-4"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
      >
        <dl className="space-y-3">
          {rows.map(({ label, value }) => (
            <div key={label} className="flex items-start gap-4">
              <dt className="text-xs font-medium w-36 flex-shrink-0 pt-0.5" style={{ color: 'var(--text-muted)' }}>
                {label}
              </dt>
              <dd className="text-sm font-mono" style={{ color: 'var(--text-primary)' }}>
                {value}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      <div
        className="flex items-start gap-3 rounded-md border p-4"
        style={{
          backgroundColor: 'rgba(245,158,11,0.08)',
          borderColor: 'rgba(245,158,11,0.3)',
        }}
      >
        <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" style={{ color: 'var(--warning)' }} />
        <p className="text-sm" style={{ color: 'var(--warning)' }}>
          This will create a new database &apos;{targetDatabaseName}&apos; in container &apos;
          {container.containerName}&apos;. Existing databases will not be affected.
        </p>
      </div>
    </div>
  )
}

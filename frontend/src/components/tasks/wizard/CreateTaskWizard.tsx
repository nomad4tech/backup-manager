import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { WizardStep1Socket } from './WizardStep1Socket'
import { WizardStep2Container } from './WizardStep2Container'
import { WizardStep3Database } from './WizardStep3Database'
import { WizardStep4Schedule, type ScheduleConfig } from './WizardStep4Schedule'
import { WizardStep5Name } from './WizardStep5Name'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { useCreateTask, useTasks } from '@/hooks/useTasks'
import { useSockets } from '@/hooks/useSockets'
import { useContainers } from '@/hooks/useContainers'
import type { ContainerInfo, DockerSocket } from '@/api/types'

interface WizardState {
  socket: DockerSocket | null
  container: ContainerInfo | null
  database: string | null
  schedule: ScheduleConfig
  name: string
}

const DEFAULT_SCHEDULE: ScheduleConfig = {
  scheduleType: 'CRON',
  cronExpression: '0 3 * * *',
  delayHours: 1,
  keepBackupsCount: '',
  compressionEnabled: true,
  uploadToS3: true,
}

const STEPS = [
  'Select Socket',
  'Select Container',
  'Select Database',
  'Configure Schedule',
  'Name & Confirm',
]

const NAME_PATTERN = /^[a-z0-9_-]+$/

export function CreateTaskWizard() {
  const navigate = useNavigate()
  const location = useLocation()
  const prefill = location.state as { socketId?: number; containerId?: string } | null

  const createTask = useCreateTask()
  const { data: tasks } = useTasks()
  const { data: sockets } = useSockets()
  const { data: prefillContainers } = useContainers(prefill?.socketId ?? 0)

  const [step, setStep] = useState(() =>
    prefill?.containerId ? 2 : prefill?.socketId ? 1 : 0,
  )
  const [state, setState] = useState<WizardState>({
    socket: null,
    container: null,
    database: null,
    schedule: DEFAULT_SCHEDULE,
    name: '',
  })

  useEffect(() => {
    if (!prefill?.socketId || !sockets) return
    const socket = sockets.find((s) => s.id === prefill.socketId) ?? null
    if (socket) setState((prev) => ({ ...prev, socket }))
  }, [sockets, prefill?.socketId])

  useEffect(() => {
    if (!prefill?.containerId || !prefillContainers) return
    const container = prefillContainers.find((c) => c.containerId === prefill.containerId) ?? null
    if (container) setState((prev) => ({ ...prev, container }))
  }, [prefillContainers, prefill?.containerId])

  function canProceed(): boolean {
    switch (step) {
      case 0:
        return state.socket != null
      case 1:
        return state.container != null
      case 2:
        return state.database != null
      case 3:
        return true
      case 4:
        return NAME_PATTERN.test(state.name)
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
    if (!state.socket || !state.container || !state.database) return

    const keepCount = state.schedule.keepBackupsCount
      ? Number(state.schedule.keepBackupsCount)
      : undefined

    createTask.mutate(
      {
        name: state.name,
        socketId: state.socket.id,
        containerId: state.container.containerId,
        databaseName: state.database,
        databaseType: state.container.databaseType,
        scheduleType: state.schedule.scheduleType,
        cronExpression:
          state.schedule.scheduleType === 'CRON'
            ? state.schedule.cronExpression
            : undefined,
        delayHours:
          state.schedule.scheduleType === 'DELAY'
            ? state.schedule.delayHours
            : undefined,
        keepBackupsCount: keepCount,
        compressionEnabled: state.schedule.compressionEnabled,
        uploadToS3: state.schedule.uploadToS3,
      },
      {
        onSuccess: () => navigate('/tasks'),
      },
    )
  }

  const isLastStep = step === STEPS.length - 1

  return (
    <div className="flex flex-col h-full max-w-2xl mx-auto">
      {/* Step indicator + title - fixed, never scrolls */}
      <div className="flex-shrink-0">
        <div className="flex items-center gap-2 mb-4">
          {STEPS.map((_label, i) => (
            <div key={i} className="flex items-center gap-2">
              <div
                className="flex items-center justify-center w-6 h-6 rounded-full text-xs font-semibold transition-colors"
                style={{
                  backgroundColor:
                    i < step
                      ? 'var(--success)'
                      : i === step
                        ? 'var(--accent)'
                        : 'var(--bg-elevated)',
                  color: i <= step ? '#fff' : 'var(--text-muted)',
                }}
              >
                {i < step ? '✓' : i + 1}
              </div>
              {i < STEPS.length - 1 && (
                <div
                  className="h-px flex-1 w-6 transition-colors"
                  style={{ backgroundColor: i < step ? 'var(--success)' : 'var(--border)' }}
                />
              )}
            </div>
          ))}
        </div>

        <h2 className="text-sm font-semibold mb-0.5" style={{ color: 'var(--text-primary)' }}>
          Step {step + 1} - {STEPS[step]}
        </h2>
        <p className="text-xs mb-3" style={{ color: 'var(--text-muted)' }}>
          {step === 0 && 'Choose a connected Docker socket'}
          {step === 1 && 'Choose a container with a PostgreSQL database'}
          {step === 2 && 'Choose the database to back up'}
          {step === 3 && 'Configure the backup schedule'}
          {step === 4 && 'Name your task and confirm'}
        </p>
      </div>

      {/* Step content - scrolls independently */}
      <div className="flex-1 overflow-y-auto min-h-0">
        {step === 0 && (
          <WizardStep1Socket
            selectedSocketId={state.socket?.id ?? null}
            onSelect={(socket) =>
              setState((s) => ({ ...s, socket, container: null, database: null }))
            }
            onNext={() => setStep((s) => s + 1)}
          />
        )}
        {step === 1 && state.socket && (
          <WizardStep2Container
            socketId={state.socket.id}
            selectedContainerId={state.container?.containerId ?? null}
            onSelect={(container) => setState((s) => ({ ...s, container, database: null }))}
            onNext={() => setStep((s) => s + 1)}
            tasks={tasks}
          />
        )}
        {step === 2 && state.socket && state.container && (
          <WizardStep3Database
            socketId={state.socket.id}
            containerId={state.container.containerId}
            selectedDatabase={state.database}
            onSelect={(database) => setState((s) => ({ ...s, database }))}
            onNext={() => setStep((s) => s + 1)}
          />
        )}
        {step === 3 && (
          <WizardStep4Schedule
            config={state.schedule}
            onChange={(schedule) => setState((s) => ({ ...s, schedule }))}
          />
        )}
        {step === 4 && state.socket && state.container && state.database && (
          <WizardStep5Name
            name={state.name}
            onNameChange={(name) => setState((s) => ({ ...s, name }))}
            socket={state.socket}
            container={state.container}
            database={state.database}
            schedule={state.schedule}
          />
        )}
      </div>

      {/* Navigation - fixed at bottom of flex column */}
      <div
        className="flex-shrink-0 py-3"
        style={{
          borderTop: '1px solid var(--border)',
          backgroundColor: 'var(--bg-base)',
        }}
      >
        {/* Error */}
        {createTask.isError && (
          <div className="mb-3">
            <ErrorMessage error={createTask.error} />
          </div>
        )}

        <div className="flex items-center justify-between">
          <button
            onClick={() => setStep((s) => s - 1)}
            disabled={step === 0}
            className="flex items-center gap-1.5 px-4 py-2 text-sm rounded-md border transition-colors disabled:opacity-40"
            style={{
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
              backgroundColor: 'transparent',
            }}
          >
            <ChevronLeft className="w-4 h-4" />
            Back
          </button>

          <button
            onClick={handleNext}
            disabled={!canProceed() || createTask.isPending}
            className="flex items-center gap-1.5 px-5 py-2 text-sm font-medium rounded-md transition-colors disabled:opacity-40"
            style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
          >
            {createTask.isPending
              ? 'Creating...'
              : isLastStep
                ? 'Create Task'
                : 'Next'}
            {!isLastStep && <ChevronRight className="w-4 h-4" />}
          </button>
        </div>
      </div>
    </div>
  )
}

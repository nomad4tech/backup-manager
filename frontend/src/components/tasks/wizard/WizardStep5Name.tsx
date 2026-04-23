import { useEffect } from 'react'
import type { DockerSocket, ContainerInfo } from '@/api/types'
import type { ScheduleConfig } from './WizardStep4Schedule'
import { formatDelay } from '@/utils/format'

interface WizardStep5NameProps {
    name: string
    onNameChange: (name: string) => void
    socket: DockerSocket
    container: ContainerInfo
    database: string
    schedule: ScheduleConfig
}

const NAME_PATTERN = /^[a-z0-9_-]+$/

const inputClass =
    'w-full rounded-md border px-3 py-2 text-sm outline-none focus:border-[var(--accent)] transition-colors font-mono'

function SummaryRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
    return (
        <div className="flex justify-between gap-4 py-2 border-b" style={{ borderColor: 'var(--border-subtle)' }}>
      <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
        {label}
      </span>
            <span
                className={`text-xs text-right ${mono ? 'font-mono' : ''}`}
                style={{ color: 'var(--text-primary)' }}
            >
        {value}
      </span>
        </div>
    )
}

export function WizardStep5Name({
                                    name,
                                    onNameChange,
                                    socket,
                                    container,
                                    database,
                                    schedule,
                                }: WizardStep5NameProps) {
    useEffect(() => {
        if (name) return

        const defaultName = [socket.name, container.containerName, database]
            .join('_')
            .toLowerCase()
            .replace(/\s+/g, '_')
            .replace(/[^a-z0-9_-]/g, '')

        onNameChange(defaultName)
    }, [socket.name, container.containerName, database])

    const isValid = NAME_PATTERN.test(name)
    const isEmpty = name.length === 0

    const scheduleText =
        schedule.scheduleType === 'CRON'
            ? schedule.cronExpression
            : formatDelay(schedule.delayHours * 3600)

    return (
        <div className="space-y-5">
            {/* Name input */}
            <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>
                    Task Name
                </label>
                <input
                    className={inputClass}
                    style={{
                        backgroundColor: 'var(--bg-base)',
                        borderColor: !isEmpty && !isValid ? 'var(--error)' : 'var(--border)',
                        color: 'var(--text-primary)',
                    }}
                    value={name}
                    onChange={(e) => onNameChange(e.target.value)}
                    placeholder="my-backup-task"
                />
                {!isEmpty && !isValid && (
                    <p className="text-xs mt-1" style={{ color: 'var(--error)' }}>
                        Only lowercase letters, numbers, underscores, and hyphens allowed
                    </p>
                )}
            </div>

            {/* Summary */}
            <div
                className="rounded-md border p-4"
                style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)' }}
            >
                <h4 className="text-xs font-semibold uppercase tracking-wide mb-3" style={{ color: 'var(--text-muted)' }}>
                    Summary
                </h4>
                <SummaryRow label="Socket" value={socket.name} />
                <SummaryRow label="Container" value={container.containerName} mono />
                <SummaryRow label="Database" value={database} mono />
                <SummaryRow label="DB Type" value={container.databaseType} />
                <SummaryRow label="Schedule" value={scheduleText} mono />
                <SummaryRow
                    label="Keep Backups"
                    value={schedule.keepBackupsCount ? `${schedule.keepBackupsCount} backups` : 'Unlimited'}
                />
                <SummaryRow label="Compression" value={schedule.compressionEnabled ? 'Enabled' : 'Disabled'} />
                <SummaryRow label="Upload to S3" value={schedule.uploadToS3 ? 'Enabled' : 'Disabled'} />
            </div>
        </div>
    )
}
import { Cron } from 'react-js-cron'
import 'react-js-cron/dist/styles.css'
import type { ScheduleType } from '@/api/types'

export interface ScheduleConfig {
  scheduleType: ScheduleType
  cronExpression: string
  delayHours: number
  keepBackupsCount: string
}

interface WizardStep4ScheduleProps {
  config: ScheduleConfig
  onChange: (config: ScheduleConfig) => void
}

const selectClass =
  'w-full rounded-md border px-3 py-2 text-sm outline-none focus:border-[var(--accent)] transition-colors'
const selectStyle = {
  backgroundColor: 'var(--bg-base)',
  borderColor: 'var(--border)',
  color: 'var(--text-primary)',
}

const inputClass =
  'w-full rounded-md border px-3 py-2 text-sm outline-none'
const inputStyle = {
  backgroundColor: 'var(--bg-elevated)',
  borderColor: 'var(--border)',
  color: 'var(--text-muted)',
}

const DELAY_OPTIONS = [1, 2, 3, 4, 6, 8, 12, 24, 48, 72, 168]

function delayLabel(hours: number): string {
  if (hours === 168) return 'Every 7 days'
  if (hours === 48) return 'Every 2 days'
  if (hours === 72) return 'Every 3 days'
  return hours === 1 ? 'Every 1 hour' : `Every ${hours} hours`
}

export function WizardStep4Schedule({ config, onChange }: WizardStep4ScheduleProps) {
  function set<K extends keyof ScheduleConfig>(key: K, value: ScheduleConfig[K]) {
    onChange({ ...config, [key]: value })
  }

  return (
    <div className="space-y-5">
      {/* Type toggle */}
      <div>
        <label className="block text-xs font-medium mb-2" style={{ color: 'var(--text-secondary)' }}>
          Schedule Type
        </label>
        <div className="flex rounded-md border overflow-hidden" style={{ borderColor: 'var(--border)' }}>
          {(['CRON', 'DELAY'] as ScheduleType[]).map((type) => (
            <button
              key={type}
              onClick={() => set('scheduleType', type)}
              className="flex-1 py-2 text-sm font-medium transition-colors"
              style={{
                backgroundColor:
                  config.scheduleType === type ? 'var(--accent)' : 'var(--bg-elevated)',
                color: config.scheduleType === type ? '#fff' : 'var(--text-secondary)',
              }}
            >
              {type}
            </button>
          ))}
        </div>
      </div>

      {/* CRON visual builder */}
      {config.scheduleType === 'CRON' && (
        <div>
          <label className="block text-xs font-medium mb-2" style={{ color: 'var(--text-secondary)' }}>
            Schedule
          </label>
          <Cron
            value={config.cronExpression}
            setValue={(val: string) => {
              if (val === '* * * * *') {
                set('cronExpression', '0 3 * * *')
              } else {
                set('cronExpression', val)
              }
            }}
            allowedPeriods={['day', 'week', 'month', 'year']}
            allowedDropdowns={['period', 'hours', 'week-days', 'months', 'month-days']}
          />
          <div className="mt-3">
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>
              Cron Expression
            </label>
            <input
              readOnly
              className={`${inputClass} font-mono`}
              style={inputStyle}
              value={config.cronExpression}
            />
          </div>
        </div>
      )}

      {/* DELAY hour picker */}
      {config.scheduleType === 'DELAY' && (
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>
            Interval
          </label>
          <select
            className={selectClass}
            style={selectStyle}
            value={config.delayHours}
            onChange={(e) => set('delayHours', Number(e.target.value))}
          >
            {DELAY_OPTIONS.map((h) => (
              <option key={h} value={h}>
                {delayLabel(h)}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Keep count */}
      <div>
        <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>
          Keep Backups Count{' '}
          <span style={{ color: 'var(--text-muted)' }}>(empty = unlimited)</span>
        </label>
        <input
          type="text"
          className={`${inputClass} font-mono`}
          style={{ ...selectStyle, borderColor: 'var(--border)' }}
          value={config.keepBackupsCount}
          onChange={(e) => {
            const val = e.target.value.replace(/[^0-9]/g, '')
            set('keepBackupsCount', val.replace(/^0+/, '') || '')
          }}
          onBlur={() => {
            if (config.keepBackupsCount === '') return
            const num = parseInt(config.keepBackupsCount)
            if (isNaN(num) || num < 1) set('keepBackupsCount', '1')
          }}
          placeholder="unlimited"
        />
      </div>
    </div>
  )
}

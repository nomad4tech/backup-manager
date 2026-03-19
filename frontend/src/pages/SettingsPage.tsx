import { useEffect, useState } from 'react'
import { useSettings, useUpdateSettings } from '@/hooks/useSettings'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import type { AppSettingsRequest, AppSettingsResponse } from '@/api/types'

// ─── Form state ──────────────────────────────────────────────────────────────

interface FormState {
  emailEnabled: boolean
  emailHost: string
  emailPort: string
  emailUsername: string
  emailFrom: string
  emailSsl: boolean
  emailStartTls: boolean
  emailTimeoutMs: string
  /** null = unchanged (keep existing) · '' = clear · 'value' = set new */
  emailPassword: string | null

  notifyOnSuccess: boolean
  notifyOnFailure: boolean
  notificationRecipients: string

  awsEnabled: boolean
  awsBucketName: string
  awsRegion: string
  awsAccessKey: string
  awsEndpoint: string
  awsPathStyleAccess: boolean
  awsDestinationDirectory: string
  /** null = unchanged (keep existing) · '' = clear · 'value' = set new */
  awsSecretKey: string | null

  heartbeatEnabled: boolean
  heartbeatUrl: string
  heartbeatIntervalSeconds: string
}

function fromSettings(s: AppSettingsResponse): FormState {
  return {
    emailEnabled: s.emailEnabled,
    emailHost: s.emailHost ?? '',
    emailPort: String(s.emailPort ?? 587),
    emailUsername: s.emailUsername ?? '',
    emailFrom: s.emailFrom ?? '',
    emailSsl: s.emailSsl,
    emailStartTls: s.emailStartTls,
    emailTimeoutMs: String(s.emailTimeoutMs),
    emailPassword: s.emailPasswordConfigured ? null : '',
    notifyOnSuccess: s.notifyOnSuccess,
    notifyOnFailure: s.notifyOnFailure,
    notificationRecipients: s.notificationRecipients ?? '',
    awsEnabled: s.awsEnabled,
    awsBucketName: s.awsBucketName ?? '',
    awsRegion: s.awsRegion ?? '',
    awsAccessKey: s.awsAccessKey ?? '',
    awsEndpoint: s.awsEndpoint ?? '',
    awsPathStyleAccess: s.awsPathStyleAccess,
    awsDestinationDirectory: s.awsDestinationDirectory ?? '',
    awsSecretKey: s.awsSecretKeyConfigured ? null : '',
    heartbeatEnabled: s.heartbeatEnabled,
    heartbeatUrl: s.heartbeatUrl ?? '',
    heartbeatIntervalSeconds: String(s.heartbeatIntervalSeconds),
  }
}

function toRequest(form: FormState): AppSettingsRequest {
  return {
    emailEnabled: form.emailEnabled,
    emailHost: form.emailHost || undefined,
    emailPort: Number(form.emailPort),
    emailUsername: form.emailUsername || undefined,
    emailPassword: form.emailPassword,
    emailFrom: form.emailFrom || undefined,
    emailSsl: form.emailSsl,
    emailStartTls: form.emailStartTls,
    emailTimeoutMs: Number(form.emailTimeoutMs),
    notifyOnSuccess: form.notifyOnSuccess,
    notifyOnFailure: form.notifyOnFailure,
    notificationRecipients: form.notificationRecipients || undefined,
    awsEnabled: form.awsEnabled,
    awsBucketName: form.awsBucketName || undefined,
    awsRegion: form.awsRegion || undefined,
    awsAccessKey: form.awsAccessKey || undefined,
    awsSecretKey: form.awsSecretKey,
    awsEndpoint: form.awsEndpoint || undefined,
    awsPathStyleAccess: form.awsPathStyleAccess,
    awsDestinationDirectory: form.awsDestinationDirectory || undefined,
    heartbeatEnabled: form.heartbeatEnabled,
    heartbeatUrl: form.heartbeatUrl || undefined,
    heartbeatIntervalSeconds: Number(form.heartbeatIntervalSeconds),
  }
}

// ─── Shared styles ────────────────────────────────────────────────────────────

const inputCls =
  'w-full rounded-md border px-3 py-2 text-sm outline-none transition-colors focus:border-[var(--accent)]'
const inputSt = {
  backgroundColor: 'var(--bg-base)',
  borderColor: 'var(--border)',
  color: 'var(--text-primary)',
}
const labelSt = { color: 'var(--text-secondary)' }
const sectionSt = {
  backgroundColor: 'var(--bg-surface)',
  borderColor: 'var(--border)',
}

// ─── Toggle ───────────────────────────────────────────────────────────────────

function Toggle({ enabled, onChange }: { enabled: boolean; onChange: () => void }) {
  return (
    <button
      type="button"
      onClick={onChange}
      className="relative w-9 h-5 rounded-full flex-shrink-0 transition-colors"
      style={{
        backgroundColor: enabled ? 'var(--accent)' : 'var(--bg-elevated)',
        border: '1px solid',
        borderColor: enabled ? 'var(--accent)' : 'var(--border)',
      }}
    >
      <span
        className="absolute top-0.5 h-4 w-4 rounded-full transition-transform"
        style={{
          left: '2px',
          backgroundColor: '#fff',
          transform: enabled ? 'translateX(16px)' : 'none',
        }}
      />
    </button>
  )
}

// ─── Connection status badge ──────────────────────────────────────────────────

function ConnectionBadge({ valid }: { valid: boolean | null }) {
  if (valid === null) {
    return (
      <span className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--text-muted)' }}>
        <span
          className="w-1.5 h-1.5 rounded-full inline-block flex-shrink-0"
          style={{ backgroundColor: 'var(--text-muted)' }}
        />
        Not checked
      </span>
    )
  }
  if (valid) {
    return (
      <span className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--success)' }}>
        <span
          className="w-1.5 h-1.5 rounded-full inline-block flex-shrink-0"
          style={{ backgroundColor: 'var(--success)' }}
        />
        Connected
      </span>
    )
  }
  return (
    <span className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--error)' }}>
      <span
        className="w-1.5 h-1.5 rounded-full inline-block flex-shrink-0"
        style={{ backgroundColor: 'var(--error)' }}
      />
      Connection failed
    </span>
  )
}

// ─── Credential field ─────────────────────────────────────────────────────────

interface CredentialFieldProps {
  label: string
  value: string | null
  isConfigured: boolean
  placeholder: string
  onChange: (v: string) => void
  onLock: () => void
  onUnlock: () => void
}

function CredentialField({
  label,
  value,
  isConfigured,
  placeholder,
  onChange,
  onLock,
  onUnlock,
}: CredentialFieldProps) {
  const btnCls =
    'px-3 py-2 text-xs rounded-md border transition-colors flex-shrink-0'
  const btnSt = {
    borderColor: 'var(--border)',
    color: 'var(--text-secondary)',
    backgroundColor: 'transparent',
  }

  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={labelSt}>
        {label}
      </label>
      {value === null ? (
        // Locked - credential is set, not changing
        <div className="flex gap-2">
          <input
            className={`${inputCls} flex-1`}
            style={{ ...inputSt, color: 'var(--text-muted)' }}
            value="••••••••"
            readOnly
            disabled
          />
          <button type="button" onClick={onUnlock} className={btnCls} style={btnSt}>
            Change
          </button>
        </div>
      ) : (
        // Unlocked - editing
        <div className="flex gap-2">
          <input
            type="password"
            className={`${inputCls} flex-1`}
            style={inputSt}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={isConfigured ? 'New value (blank = clear)' : placeholder}
            autoFocus
          />
          {isConfigured && (
            <button type="button" onClick={onLock} className={btnCls} style={btnSt}>
              Cancel
            </button>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function SettingsPage() {
  const { data: settings, isLoading, isError, error: loadError, refetch } = useSettings()
  const update = useUpdateSettings()
  const [form, setForm] = useState<FormState | null>(null)
  const [savedOk, setSavedOk] = useState(false)

  // Initialize form once when settings load
  useEffect(() => {
    if (settings && form === null) setForm(fromSettings(settings))
  }, [settings, form])

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev))
  }

  function handleSave() {
    if (!form) return
    setSavedOk(false)
    update.mutate(toRequest(form), {
      onSuccess: (data) => {
        setSavedOk(true)
        setTimeout(() => setSavedOk(false), 2500)
        // Reset credential fields based on what the server now reports
        setForm((prev) =>
          prev
            ? {
                ...prev,
                emailPassword: data.emailPasswordConfigured ? null : '',
                awsSecretKey: data.awsSecretKeyConfigured ? null : '',
              }
            : null,
        )
      },
    })
  }

  // ─── Loading ───────────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className="p-6 max-w-2xl">
        <div
          className="h-8 w-36 rounded-md animate-pulse mb-6"
          style={{ backgroundColor: 'var(--bg-surface)' }}
        />
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            className="h-28 rounded-lg border animate-pulse mb-4"
            style={sectionSt}
          />
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <div className="p-6">
        <ErrorMessage error={loadError} onRetry={refetch} />
      </div>
    )
  }

  if (!form || !settings) return null

  // ─── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
            Settings
          </h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>
            Email, AWS S3, and monitoring integrations
          </p>
        </div>
        <button
          onClick={handleSave}
          disabled={update.isPending}
          className="px-4 py-2 text-sm font-medium rounded-md transition-colors disabled:opacity-60"
          style={{
            backgroundColor: savedOk ? 'var(--success)' : 'var(--accent)',
            color: '#fff',
          }}
        >
          {update.isPending ? 'Saving…' : savedOk ? 'Saved' : 'Save'}
        </button>
      </div>

      {update.isError && (
        <div className="mb-4 max-w-2xl">
          <ErrorMessage error={update.error} />
        </div>
      )}

      <div className="space-y-4 max-w-2xl">
        {/* ── Email ──────────────────────────────────────────────────────── */}
        <div className="rounded-lg border" style={sectionSt}>
          <div className="px-5 py-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  Email
                </h2>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  SMTP server for sending notifications
                </p>
              </div>
              <div className="flex items-center gap-3">
                {form.emailEnabled && (
                  <ConnectionBadge valid={settings.emailConnectionValid} />
                )}
                <Toggle
                  enabled={form.emailEnabled}
                  onChange={() => set('emailEnabled', !form.emailEnabled)}
                />
              </div>
            </div>

            {form.emailEnabled && (
              <div className="mt-4 space-y-4">
                {/* Host + Port */}
                <div className="grid grid-cols-3 gap-3">
                  <div className="col-span-2">
                    <label className="block text-xs font-medium mb-1" style={labelSt}>
                      SMTP Host
                    </label>
                    <input
                      className={`${inputCls} font-mono`}
                      style={inputSt}
                      value={form.emailHost}
                      onChange={(e) => set('emailHost', e.target.value)}
                      placeholder="smtp.example.com"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium mb-1" style={labelSt}>
                      Port
                    </label>
                    <input
                      type="text"
                      className={`${inputCls} font-mono`}
                      style={inputSt}
                      value={form.emailPort}
                      onChange={(e) => {
                        const val = e.target.value.replace(/[^0-9]/g, '')
                        set('emailPort', val.replace(/^0+/, '') || '')
                      }}
                      onBlur={() => {
                        const num = parseInt(form.emailPort)
                        if (isNaN(num) || num < 1) set('emailPort', '1')
                        else if (num > 65535) set('emailPort', '65535')
                      }}
                    />
                  </div>
                </div>

                {/* Security */}
                <div>
                  <label className="block text-xs font-medium mb-2" style={labelSt}>
                    Security
                  </label>
                  <div
                    className="flex rounded-md border overflow-hidden"
                    style={{ borderColor: 'var(--border)' }}
                  >
                    {(
                      [
                        { label: 'None', ssl: false, tls: false },
                        { label: 'STARTTLS', ssl: false, tls: true },
                        { label: 'SSL/TLS', ssl: true, tls: false },
                      ] as const
                    ).map((opt) => {
                      const active =
                        form.emailSsl === opt.ssl && form.emailStartTls === opt.tls
                      return (
                        <button
                          key={opt.label}
                          type="button"
                          onClick={() =>
                            setForm((p) =>
                              p ? { ...p, emailSsl: opt.ssl, emailStartTls: opt.tls } : p,
                            )
                          }
                          className="flex-1 py-1.5 text-xs font-medium transition-colors"
                          style={{
                            backgroundColor: active ? 'var(--accent)' : 'var(--bg-elevated)',
                            color: active ? '#fff' : 'var(--text-secondary)',
                          }}
                        >
                          {opt.label}
                        </button>
                      )
                    })}
                  </div>
                </div>

                {/* Username + From */}
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium mb-1" style={labelSt}>
                      Username
                    </label>
                    <input
                      className={inputCls}
                      style={inputSt}
                      value={form.emailUsername}
                      onChange={(e) => set('emailUsername', e.target.value)}
                      placeholder="user@example.com"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium mb-1" style={labelSt}>
                      From address
                    </label>
                    <input
                      className={inputCls}
                      style={inputSt}
                      value={form.emailFrom}
                      onChange={(e) => set('emailFrom', e.target.value)}
                      placeholder="Backup Manager <noreply@example.com>"
                    />
                  </div>
                </div>

                {/* Password */}
                <CredentialField
                  label="Password"
                  value={form.emailPassword}
                  isConfigured={settings.emailPasswordConfigured}
                  placeholder="Optional"
                  onChange={(v) => set('emailPassword', v)}
                  onUnlock={() => set('emailPassword', '')}
                  onLock={() => set('emailPassword', null)}
                />

                {/* Timeout */}
                <div className="w-48">
                  <label className="block text-xs font-medium mb-1" style={labelSt}>
                    Timeout (ms)
                  </label>
                  <input
                    type="text"
                    className={`${inputCls} font-mono`}
                    style={inputSt}
                    value={form.emailTimeoutMs}
                    onChange={(e) => {
                      const val = e.target.value.replace(/[^0-9]/g, '')
                      set('emailTimeoutMs', val.replace(/^0+/, '') || '')
                    }}
                    onBlur={() => {
                      const num = parseInt(form.emailTimeoutMs)
                      if (isNaN(num) || num < 1000) set('emailTimeoutMs', '1000')
                    }}
                  />
                </div>
              </div>
            )}
          </div>
        </div>

        {/* ── Notifications ──────────────────────────────────────────────── */}
        <div className="rounded-lg border" style={sectionSt}>
          <div className="px-5 py-4">
            <h2 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
              Notifications
            </h2>
            <p className="text-xs mb-4" style={{ color: 'var(--text-muted)' }}>
              Email alerts sent after each backup job
            </p>

            <div className="space-y-3">
              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.notifyOnSuccess}
                  onChange={(e) => set('notifyOnSuccess', e.target.checked)}
                  className="w-4 h-4 rounded mt-0.5 flex-shrink-0"
                  style={{ accentColor: 'var(--accent)' }}
                />
                <div>
                  <span className="text-sm" style={{ color: 'var(--text-primary)' }}>
                    Notify on success
                  </span>
                  <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
                    Send email when a backup completes successfully
                  </p>
                </div>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.notifyOnFailure}
                  onChange={(e) => set('notifyOnFailure', e.target.checked)}
                  className="w-4 h-4 rounded mt-0.5 flex-shrink-0"
                  style={{ accentColor: 'var(--accent)' }}
                />
                <div>
                  <span className="text-sm" style={{ color: 'var(--text-primary)' }}>
                    Notify on failure
                  </span>
                  <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
                    Send email when a backup fails
                  </p>
                </div>
              </label>

              <div className="pt-1">
                <label className="block text-xs font-medium mb-1" style={labelSt}>
                  Recipients
                </label>
                <input
                  className={inputCls}
                  style={inputSt}
                  value={form.notificationRecipients}
                  onChange={(e) => set('notificationRecipients', e.target.value)}
                  placeholder="ops@example.com, admin@example.com"
                />
                <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
                  Comma-separated list of email addresses
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* ── AWS S3 ─────────────────────────────────────────────────────── */}
        <div className="rounded-lg border" style={sectionSt}>
          <div className="px-5 py-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  AWS S3
                </h2>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  Upload backups to S3-compatible object storage
                </p>
              </div>
              <div className="flex items-center gap-3">
                {form.awsEnabled && (
                  <ConnectionBadge valid={settings.awsConnectionValid} />
                )}
                <Toggle
                  enabled={form.awsEnabled}
                  onChange={() => set('awsEnabled', !form.awsEnabled)}
                />
              </div>
            </div>

            {form.awsEnabled && (
              <div className="mt-4 space-y-4">
                {/* Bucket + Region */}
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium mb-1" style={labelSt}>
                      Bucket name
                    </label>
                    <input
                      className={`${inputCls} font-mono`}
                      style={inputSt}
                      value={form.awsBucketName}
                      onChange={(e) => set('awsBucketName', e.target.value)}
                      placeholder="my-backups"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium mb-1" style={labelSt}>
                      Region
                    </label>
                    <input
                      className={`${inputCls} font-mono`}
                      style={inputSt}
                      value={form.awsRegion}
                      onChange={(e) => set('awsRegion', e.target.value)}
                      placeholder="us-east-1"
                    />
                  </div>
                </div>

                {/* Access Key */}
                <div>
                  <label className="block text-xs font-medium mb-1" style={labelSt}>
                    Access key ID
                  </label>
                  <input
                    className={`${inputCls} font-mono`}
                    style={inputSt}
                    value={form.awsAccessKey}
                    onChange={(e) => set('awsAccessKey', e.target.value)}
                    placeholder="AKIAIOSFODNN7EXAMPLE"
                  />
                </div>

                {/* Secret Key */}
                <CredentialField
                  label="Secret access key"
                  value={form.awsSecretKey}
                  isConfigured={settings.awsSecretKeyConfigured}
                  placeholder="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
                  onChange={(v) => set('awsSecretKey', v)}
                  onUnlock={() => set('awsSecretKey', '')}
                  onLock={() => set('awsSecretKey', null)}
                />

                {/* Custom endpoint */}
                <div>
                  <label className="block text-xs font-medium mb-1" style={labelSt}>
                    Custom endpoint{' '}
                    <span style={{ color: 'var(--text-muted)' }}>
                      (optional - for Yandex Cloud, MinIO, etc.)
                    </span>
                  </label>
                  <input
                    className={`${inputCls} font-mono`}
                    style={inputSt}
                    value={form.awsEndpoint}
                    onChange={(e) => set('awsEndpoint', e.target.value)}
                    placeholder="https://storage.yandexcloud.net"
                  />
                </div>

                {/* Destination directory */}
                <div>
                  <label className="block text-xs font-medium mb-1" style={labelSt}>
                    Destination directory{' '}
                    <span style={{ color: 'var(--text-muted)' }}>
                      (optional prefix inside bucket)
                    </span>
                  </label>
                  <input
                    className={`${inputCls} font-mono`}
                    style={inputSt}
                    value={form.awsDestinationDirectory}
                    onChange={(e) => set('awsDestinationDirectory', e.target.value)}
                    placeholder="backups/prod"
                  />
                </div>

                {/* Path-style access */}
                <label className="flex items-start gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.awsPathStyleAccess}
                    onChange={(e) => set('awsPathStyleAccess', e.target.checked)}
                    className="w-4 h-4 rounded mt-0.5 flex-shrink-0"
                    style={{ accentColor: 'var(--accent)' }}
                  />
                  <div>
                    <span className="text-sm" style={{ color: 'var(--text-primary)' }}>
                      Path-style access
                    </span>
                    <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
                      Use /bucket/key URLs instead of bucket.host/key - required for MinIO
                    </p>
                  </div>
                </label>
              </div>
            )}
          </div>
        </div>

        {/* ── Heartbeat ──────────────────────────────────────────────────── */}
        <div className="rounded-lg border" style={sectionSt}>
          <div className="px-5 py-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  Heartbeat
                </h2>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  Periodic pings to an uptime monitoring service
                </p>
              </div>
              <div className="flex items-center gap-3">
                {form.heartbeatEnabled && (
                  <ConnectionBadge valid={settings.heartbeatConnectionValid} />
                )}
                <Toggle
                  enabled={form.heartbeatEnabled}
                  onChange={() => set('heartbeatEnabled', !form.heartbeatEnabled)}
                />
              </div>
            </div>

            {form.heartbeatEnabled && (
              <div className="mt-4 space-y-4">
                <div>
                  <label className="block text-xs font-medium mb-1" style={labelSt}>
                    Ping URL
                  </label>
                  <input
                    className={`${inputCls} font-mono`}
                    style={inputSt}
                    value={form.heartbeatUrl}
                    onChange={(e) => set('heartbeatUrl', e.target.value)}
                    placeholder="https://uptime.example.com/api/push/xxxx"
                  />
                </div>
                <div className="w-48">
                  <label className="block text-xs font-medium mb-1" style={labelSt}>
                    Interval{' '}
                    <span style={{ color: 'var(--text-muted)' }}>(seconds, min 30)</span>
                  </label>
                  <input
                    type="text"
                    className={`${inputCls} font-mono`}
                    style={inputSt}
                    value={form.heartbeatIntervalSeconds}
                    onChange={(e) => {
                      const val = e.target.value.replace(/[^0-9]/g, '')
                      set('heartbeatIntervalSeconds', val.replace(/^0+/, '') || '')
                    }}
                    onBlur={() => {
                      const num = parseInt(form.heartbeatIntervalSeconds)
                      if (isNaN(num) || num < 30) set('heartbeatIntervalSeconds', '30')
                    }}
                  />
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

import { useState } from 'react'
import type { CreateSocketRequest } from '@/api/types'

interface SocketFormProps {
  onSubmit: (data: CreateSocketRequest) => void
  onCancel: () => void
  loading?: boolean
}

// UI-only: determines which credential field to show
type AuthMode = 'KEY' | 'PASSWORD'

interface FormState extends Omit<CreateSocketRequest, 'sshPort'> {
  sshPort: string
  _authMode: AuthMode
}

const DEFAULT_STATE: FormState = {
  name: '',
  sshHost: '',
  sshPort: '22',
  sshUser: '',
  sshPassword: '',
  sshPrivateKeyPath: '',
  _authMode: 'KEY',
}

const inputClass =
  'w-full rounded-md border px-3 py-2 text-sm outline-none transition-colors focus:border-[var(--accent)]'
const inputStyle = {
  backgroundColor: 'var(--bg-base)',
  borderColor: 'var(--border)',
  color: 'var(--text-primary)',
}
const labelStyle = { color: 'var(--text-secondary)' }

export function SocketForm({ onSubmit, onCancel, loading }: SocketFormProps) {
  const [form, setForm] = useState<FormState>(DEFAULT_STATE)

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const { _authMode, sshPort, ...data } = form
    // Only send the relevant credential field
    onSubmit({
      ...data,
      sshPort: Number(sshPort) || 22,
      sshPassword: _authMode === 'PASSWORD' ? data.sshPassword : undefined,
      sshPrivateKeyPath: _authMode === 'KEY' ? data.sshPrivateKeyPath : undefined,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {/* Name */}
      <div>
        <label className="block text-xs font-medium mb-1" style={labelStyle}>
          Name
        </label>
        <input
          className={inputClass}
          style={inputStyle}
          value={form.name}
          onChange={(e) => set('name', e.target.value)}
          placeholder="my-ssh-socket"
          required
        />
      </div>

      {/* Host + Port */}
      <div className="grid grid-cols-3 gap-3">
        <div className="col-span-2">
          <label className="block text-xs font-medium mb-1" style={labelStyle}>
            SSH Host
          </label>
          <input
            className={`${inputClass} font-mono`}
            style={inputStyle}
            value={form.sshHost}
            onChange={(e) => set('sshHost', e.target.value)}
            placeholder="192.168.1.100"
            required
          />
        </div>
        <div>
          <label className="block text-xs font-medium mb-1" style={labelStyle}>
            SSH Port
          </label>
          <input
            type="text"
            className={`${inputClass} font-mono`}
            style={inputStyle}
            value={form.sshPort}
            onChange={(e) => {
              const val = e.target.value.replace(/[^0-9]/g, '')
              set('sshPort', val.replace(/^0+/, '') || '')
            }}
            onBlur={() => {
              const num = parseInt(form.sshPort)
              if (isNaN(num) || num < 1) set('sshPort', '1')
              else if (num > 65535) set('sshPort', '65535')
            }}
          />
        </div>
      </div>

      {/* Username */}
      <div>
        <label className="block text-xs font-medium mb-1" style={labelStyle}>
          SSH User
        </label>
        <input
          className={inputClass}
          style={inputStyle}
          value={form.sshUser}
          onChange={(e) => set('sshUser', e.target.value)}
          placeholder="root"
          required
        />
      </div>

      {/* Auth mode toggle */}
      <div>
        <label className="block text-xs font-medium mb-2" style={labelStyle}>
          Authentication
        </label>
        <div className="flex rounded-md border overflow-hidden" style={{ borderColor: 'var(--border)' }}>
          {(['KEY', 'PASSWORD'] as AuthMode[]).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => set('_authMode', mode)}
              className="flex-1 py-1.5 text-xs font-medium transition-colors"
              style={{
                backgroundColor: form._authMode === mode ? 'var(--accent)' : 'var(--bg-elevated)',
                color: form._authMode === mode ? '#fff' : 'var(--text-secondary)',
              }}
            >
              {mode === 'KEY' ? 'SSH Key Path' : 'Password'}
            </button>
          ))}
        </div>
      </div>

      {form._authMode === 'PASSWORD' && (
        <div>
          <label className="block text-xs font-medium mb-1" style={labelStyle}>
            Password
          </label>
          <input
            type="password"
            className={inputClass}
            style={inputStyle}
            value={form.sshPassword}
            onChange={(e) => set('sshPassword', e.target.value)}
          />
        </div>
      )}

      {form._authMode === 'KEY' && (
        <div>
          <label className="block text-xs font-medium mb-1" style={labelStyle}>
            Private Key Path{' '}
            <span style={{ color: 'var(--text-muted)' }}>(path on server)</span>
          </label>
          <input
            className={`${inputClass} font-mono`}
            style={inputStyle}
            value={form.sshPrivateKeyPath}
            onChange={(e) => set('sshPrivateKeyPath', e.target.value)}
            placeholder="/home/user/.ssh/id_rsa"
          />
        </div>
      )}

      {/* Actions */}
      <div className="flex gap-3 justify-end pt-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm rounded-md border transition-colors"
          style={{
            borderColor: 'var(--border)',
            color: 'var(--text-secondary)',
            backgroundColor: 'transparent',
          }}
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={loading}
          className="px-4 py-2 text-sm font-medium rounded-md transition-colors disabled:opacity-50"
          style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
        >
          {loading ? 'Saving...' : 'Save Socket'}
        </button>
      </div>
    </form>
  )
}

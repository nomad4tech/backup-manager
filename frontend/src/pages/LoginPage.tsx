import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Database } from 'lucide-react'
import { useLogin } from '@/hooks/useAuth'

const inputCls =
  'w-full rounded-md border px-3 py-2 text-sm outline-none transition-colors focus:border-[var(--accent)]'
const inputSt = {
  backgroundColor: 'var(--bg-base)',
  borderColor: 'var(--border)',
  color: 'var(--text-primary)',
}

export function LoginPage() {
  const navigate = useNavigate()
  const login = useLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [error, setError] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    login.mutate({ username, password, rememberMe }, {
      onSuccess: () => {
        navigate('/', { replace: true })
      },
      onError: () => {
        setError('Invalid username or password')
      },
    })
  }

  return (
    <div
      className="min-h-screen flex items-center justify-center p-4"
      style={{ backgroundColor: 'var(--bg-base)' }}
    >
      <div
        className="w-full max-w-sm rounded-lg border p-8"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border)' }}
      >
        {/* Logo */}
        <div className="flex items-center gap-2 mb-7">
          <div
            className="w-7 h-7 rounded flex items-center justify-center flex-shrink-0"
            style={{ backgroundColor: 'var(--accent)' }}
          >
            <Database className="w-4 h-4 text-white" />
          </div>
          <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
            Backup Manager
          </span>
        </div>

        <h1 className="text-base font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
          Sign in
        </h1>
        <p className="text-xs mb-6" style={{ color: 'var(--text-muted)' }}>
          Enter your credentials to continue
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>
              Username
            </label>
            <input
              type="text"
              required
              className={inputCls}
              style={inputSt}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
              autoComplete="username"
            />
          </div>

          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>
              Password
            </label>
            <input
              type="password"
              required
              className={inputCls}
              style={inputSt}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>

          <label className="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="w-4 h-4 rounded flex-shrink-0"
              style={{ accentColor: 'var(--accent)' }}
            />
            <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>
              Remember me for 30 days
            </span>
          </label>

          {error && (
            <p className="text-xs" style={{ color: 'var(--error)' }}>
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={login.isPending}
            className="w-full py-2 text-sm font-medium rounded-md transition-colors disabled:opacity-60"
            style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
          >
            {login.isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}

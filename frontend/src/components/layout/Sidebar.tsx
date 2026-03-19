import { NavLink } from 'react-router-dom'
import { Database, Github, History, Server, Settings } from 'lucide-react'
import { useSettings } from '@/hooks/useSettings'

interface NavItem {
  to: string
  icon: React.ReactNode
  label: string
  end?: boolean
}

const NAV_ITEMS: NavItem[] = [
  { to: '/sockets', icon: <Server className="w-4 h-4" />, label: 'Docker Sockets', end: true },
  { to: '/tasks', icon: <Database className="w-4 h-4" />, label: 'Backup Tasks' },
  { to: '/history', icon: <History className="w-4 h-4" />, label: 'History' },
]

function IntegrationStatus() {
  const { data: settings } = useSettings()

  if (!settings) return null

  const items = [
    { label: 'Email', enabled: settings.emailEnabled, valid: settings.emailConnectionValid },
    { label: 'AWS S3', enabled: settings.awsEnabled, valid: settings.awsConnectionValid },
    { label: 'Heartbeat', enabled: settings.heartbeatEnabled, valid: settings.heartbeatConnectionValid },
  ].filter((i) => i.enabled)

  if (items.length === 0) return null

  return (
      <div className="mb-1 space-y-0.5">
        {items.map((item) => {
          const dotColor =
              item.valid === true
                  ? 'var(--success)'
                  : item.valid === false
                      ? 'var(--error)'
                      : 'var(--border)'
          return (
              <div key={item.label} className="flex items-center justify-between px-3 py-0.5">
            <span className="text-xs" style={{ color: 'var(--text-muted)', opacity: 0.85 }}>
              {item.label}
            </span>
                <span
                    className="w-1.5 h-1.5 rounded-full flex-shrink-0"
                    style={{ backgroundColor: dotColor }}
                />
              </div>
          )
        })}
      </div>
  )
}

export function Sidebar() {
  return (
      <aside
          className="flex flex-col flex-shrink-0 h-full border-r"
          style={{
            width: '240px',
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border)',
          }}
      >
        {/* Logo */}
        <div
            className="flex items-center gap-2 px-5 py-4 border-b"
            style={{ borderColor: 'var(--border)' }}
        >
          <div
              className="w-6 h-6 rounded flex items-center justify-center flex-shrink-0"
              style={{ backgroundColor: 'var(--accent)' }}
          >
            <Database className="w-3.5 h-3.5 text-white" />
          </div>
          <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          Backup Manager
        </span>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {NAV_ITEMS.map((item) => (
              <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                      `flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${
                          isActive ? 'nav-active' : 'nav-inactive'
                      }`
                  }
                  style={({ isActive }) => ({
                    backgroundColor: isActive ? 'rgba(59,130,246,0.12)' : 'transparent',
                    color: isActive ? 'var(--accent)' : 'var(--text-secondary)',
                  })}
              >
                {item.icon}
                <span>{item.label}</span>
              </NavLink>
          ))}
        </nav>

        {/* Settings at bottom */}
        <div className="px-3 pt-3 pb-4 border-t" style={{ borderColor: 'var(--border)' }}>
          <IntegrationStatus />
          <NavLink
              to="/settings"
              className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${
                      isActive ? 'nav-active' : 'nav-inactive'
                  }`
              }
              style={({ isActive }) => ({
                backgroundColor: isActive ? 'rgba(59,130,246,0.12)' : 'transparent',
                color: isActive ? 'var(--accent)' : 'var(--text-secondary)',
              })}
          >
            <Settings className="w-4 h-4" />
            <span>Settings</span>
          </NavLink>
          <div className="-mx-3 border-b my-2" style={{ borderColor: 'var(--border)', opacity: 0.5 }} />
          <div className="mt-2">
            <a
              href="https://github.com/nomad4tech/backup-manager"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-between"
              style={{ color: 'var(--text-muted)', opacity: 0.45, fontSize: '0.7rem', textDecoration: 'none' }}
            >
              <span>v1.0.0 · GitHub</span>
              <Github className="w-3 h-3" />
            </a>
          </div>
        </div>
      </aside>
  )
}
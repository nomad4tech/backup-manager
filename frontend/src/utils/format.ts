export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const value = bytes / Math.pow(1024, i)
  return `${value % 1 === 0 ? value : value.toFixed(1)} ${units[i]}`
}

export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  if (hours > 0) return `${hours}h ${minutes}m ${seconds}s`
  if (minutes > 0) return `${minutes}m ${seconds}s`
  return `${seconds}s`
}

export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(iso))
}

export function formatDelay(seconds: number): string {
  if (seconds < 60) return `every ${seconds} second${seconds !== 1 ? 's' : ''}`
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `every ${days} day${days !== 1 ? 's' : ''}`
  if (hours > 0) return `every ${hours} hour${hours !== 1 ? 's' : ''}`
  return `every ${minutes} minute${minutes !== 1 ? 's' : ''}`
}

export function formatCronHuman(expression: string): string {
  const parts = expression.trim().split(/\s+/)
  if (parts.length !== 5) return expression

  const [minute, hour, dom, month, dow] = parts

  // Simple common patterns
  if (dom === '*' && month === '*' && dow === '*') {
    if (minute === '0' && hour !== '*') {
      return `every day at ${hour}:00`
    }
    if (minute !== '*' && hour !== '*') {
      return `every day at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`
    }
    if (minute === '*' && hour === '*') {
      return 'every minute'
    }
  }

  return expression
}

import { AlertCircle, RefreshCw } from 'lucide-react'
import { ApiError } from '@/api/client'

interface ErrorMessageProps {
  error: unknown
  onRetry?: () => void
}

function getErrorText(error: unknown): string {
  if (error instanceof ApiError) return `Error ${error.status}: ${error.message}`
  if (error instanceof Error) return error.message
  return 'An unexpected error occurred'
}

export function ErrorMessage({ error, onRetry }: ErrorMessageProps) {
  return (
    <div
      className="flex items-start gap-3 rounded-md border p-4"
      style={{
        backgroundColor: 'rgba(239,68,68,0.08)',
        borderColor: 'rgba(239,68,68,0.3)',
      }}
    >
      <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" style={{ color: 'var(--error)' }} />
      <div className="flex-1 min-w-0">
        <p className="text-sm" style={{ color: 'var(--error)' }}>
          {getErrorText(error)}
        </p>
      </div>
      {onRetry && (
        <button
          onClick={onRetry}
          className="flex items-center gap-1.5 text-xs px-2 py-1 rounded transition-colors"
          style={{
            color: 'var(--text-secondary)',
            backgroundColor: 'var(--bg-elevated)',
          }}
        >
          <RefreshCw className="w-3 h-3" />
          Retry
        </button>
      )}
    </div>
  )
}

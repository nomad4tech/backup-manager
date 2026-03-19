import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { CreateTaskWizard } from '@/components/tasks/wizard/CreateTaskWizard'

export function TaskCreatePage() {
  return (
    <div className="h-full flex flex-col p-6 overflow-hidden">
      <Link
        to="/tasks"
        className="inline-flex items-center gap-1.5 text-sm mb-6 transition-colors hover:opacity-80 flex-shrink-0"
        style={{ color: 'var(--text-secondary)' }}
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Tasks
      </Link>

      <div className="mb-8 flex-shrink-0">
        <h1 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
          New Backup Task
        </h1>
        <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>
          Configure a new scheduled backup
        </p>
      </div>

      <div className="flex-1 min-h-0">
        <CreateTaskWizard />
      </div>
    </div>
  )
}

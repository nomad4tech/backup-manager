import type {
  AppSettingsRequest,
  AppSettingsResponse,
  BackupRecord,
  BackupTask,
  ContainerInfo,
  CreateBackupTaskRequest,
  CreateSocketRequest,
  DockerSocket,
  HistoryFilters,
  Page,
  UpdateBackupTaskRequest,
  UpdateSocketRequest,
} from './types'

const BASE_URL = 'http://localhost:8080'

// ──────────────────────────────────────────
// Base fetch wrapper
// ──────────────────────────────────────────

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  })

  if (!res.ok) {
    if (res.status === 401 && window.location.pathname !== '/login' && path !== '/api/auth/me') {
      window.location.href = '/login'
      return undefined as T
    }
    let message = res.statusText
    try {
      const body = await res.json() as { message?: string; error?: string }
      message = body.message ?? body.error ?? message
    } catch {
      // ignore parse error
    }
    throw new ApiError(res.status, message)
  }

  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T
  }

  return res.json() as Promise<T>
}

// ──────────────────────────────────────────
// Sockets
// ──────────────────────────────────────────

export const socketsApi = {
  list: () => request<DockerSocket[]>('/api/sockets'),

  create: (data: CreateSocketRequest) =>
    request<DockerSocket>('/api/sockets', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (id: number, data: UpdateSocketRequest) =>
    request<DockerSocket>(`/api/sockets/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  delete: (id: number) =>
    request<void>(`/api/sockets/${id}`, { method: 'DELETE' }),

  connect: (id: number) =>
    request<DockerSocket>(`/api/sockets/${id}/connect`, { method: 'POST' }),

  disconnect: (id: number) =>
    request<DockerSocket>(`/api/sockets/${id}/disconnect`, { method: 'POST' }),
}

// ──────────────────────────────────────────
// Discovery
// ──────────────────────────────────────────

export const discoveryApi = {
  containers: (socketId: number) =>
    request<ContainerInfo[]>(
      `/api/discovery/sockets/${socketId}/containers`,
    ),

  container: (socketId: number, containerId: string) =>
    request<ContainerInfo>(
      `/api/discovery/sockets/${socketId}/containers/${containerId}`,
    ),

  databases: (socketId: number, containerId: string) =>
    request<string[]>(
      `/api/discovery/sockets/${socketId}/containers/${containerId}/databases`,
    ),
}

// ──────────────────────────────────────────
// Backup Tasks
// ──────────────────────────────────────────

export const tasksApi = {
  list: () => request<BackupTask[]>('/api/backup-tasks'),

  create: (data: CreateBackupTaskRequest) =>
    request<BackupTask>('/api/backup-tasks', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  get: (id: number) => request<BackupTask>(`/api/backup-tasks/${id}`),

  update: (id: number, data: UpdateBackupTaskRequest) =>
    request<BackupTask>(`/api/backup-tasks/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  toggle: (id: number) =>
    request<BackupTask>(`/api/backup-tasks/${id}/toggle`, { method: 'PATCH' }),

  delete: (id: number) =>
    request<void>(`/api/backup-tasks/${id}`, { method: 'DELETE' }),

  forceRun: (id: number) =>
    fetch(`${BASE_URL}/api/scheduler/tasks/${id}/run`, { method: 'POST' }),
}

// ──────────────────────────────────────────
// Backup History
// ──────────────────────────────────────────

function buildHistoryQuery(filters: HistoryFilters): string {
  const params = new URLSearchParams()
  if (filters.taskId != null) params.set('taskId', String(filters.taskId))
  if (filters.status) params.set('status', filters.status)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.page != null) params.set('page', String(filters.page))
  if (filters.size != null) params.set('size', String(filters.size))
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

// ──────────────────────────────────────────
// Settings
// ──────────────────────────────────────────

export const settingsApi = {
  get: () => request<AppSettingsResponse>('/api/settings'),

  update: (data: AppSettingsRequest) =>
    request<AppSettingsResponse>('/api/settings', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
}

// ──────────────────────────────────────────
// Backup History
// ──────────────────────────────────────────

export const historyApi = {
  list: (filters: HistoryFilters = {}) =>
    request<Page<BackupRecord>>(`/api/backup-history${buildHistoryQuery(filters)}`),

  get: (id: number) => request<BackupRecord>(`/api/backup-history/${id}`),

  delete: (id: number) =>
    request<void>(`/api/backup-history/${id}`, { method: 'DELETE' }),
}

// ──────────────────────────────────────────
// Auth
// ──────────────────────────────────────────

export const authApi = {
  me: () => request<{ username: string }>('/api/auth/me'),

  login: (username: string, password: string, rememberMe: boolean) =>
    request<{ username: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password, rememberMe }),
    }),

  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),

  changeCredentials: (currentPassword: string, newUsername?: string, newPassword?: string) =>
    request<void>('/api/auth/credentials', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newUsername, newPassword }),
    }),
}

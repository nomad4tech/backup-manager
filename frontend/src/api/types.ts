// ──────────────────────────────────────────
// Sockets
// ──────────────────────────────────────────

export type SocketType = 'LOCAL' | 'REMOTE_SSH'
export type SocketStatus = 'CONNECTED' | 'DISCONNECTED' | 'ERROR'

export interface DockerSocket {
  id: number
  name: string
  type: SocketType
  status: SocketStatus
  socketPath?: string
  sshHost?: string
  sshPort?: number
  sshUser?: string
  lastConnected?: string
  lastError?: string
  isSystem?: boolean
  socatInfo?: string
  socatStatus?: string
}

// Only SSH sockets can be created via API. LOCAL socket is auto-created by the backend.
export interface CreateSocketRequest {
  name: string
  sshHost: string
  sshPort?: number
  sshUser: string
  sshPassword?: string
  sshPrivateKeyPath?: string
  remoteDockerSocketPath?: string
  remoteSocatPort?: number
}

export type UpdateSocketRequest = CreateSocketRequest

// ──────────────────────────────────────────
// Discovery
// ──────────────────────────────────────────

export type DatabaseType = 'POSTGRES' | 'MYSQL' | 'MARIADB'
export type ContainerStatus = 'RUNNING' | 'STOPPED' | 'PAUSED'

export interface ContainerInfo {
  containerId: string
  containerName: string
  databaseType: DatabaseType
  databaseTypeName?: string
  imageName: string
  imageVersion?: string
  state: ContainerStatus
  exposedPorts: number[]
}

// ──────────────────────────────────────────
// Backup Tasks
// ──────────────────────────────────────────

export type ScheduleType = 'CRON' | 'DELAY'
export type TaskStatus = 'IDLE' | 'RUNNING' | 'ERROR' | 'DISABLED'

export interface BackupTask {
  id: number
  name: string
  socketId: number
  socketName?: string
  containerId: string
  containerName: string
  databaseName: string
  databaseType: DatabaseType
  scheduleType: ScheduleType
  cronExpression?: string
  delayHours?: number
  keepBackupsCount?: number
  compressionEnabled: boolean
  uploadToS3: boolean
  enabled: boolean
  status: TaskStatus
  lastError?: string
  nextScheduledAt?: string
}

export interface CreateBackupTaskRequest {
  name: string
  socketId: number
  containerId: string
  databaseName: string
  databaseType: DatabaseType
  scheduleType: ScheduleType
  cronExpression?: string
  delayHours?: number
  keepBackupsCount?: number
  compressionEnabled: boolean
  uploadToS3: boolean
}

export type UpdateBackupTaskRequest = CreateBackupTaskRequest

// ──────────────────────────────────────────
// Backup History
// ──────────────────────────────────────────

export type BackupStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'UPLOADING' | 'UPLOADED' | 'UPLOAD_FAILED' | 'SEEDED'

export interface BackupRecord {
  id: number
  taskId?: number
  taskName: string
  socketId: number
  containerId: string
  containerName?: string
  databaseName: string
  status: BackupStatus
  startedAt: string
  completedAt?: string
  durationMs?: number
  filePath?: string
  fileSizeBytes?: number
  errorMessage?: string
  awsKey?: string
  awsBucketName?: string
}

export interface HistoryFilters {
  taskId?: number
  status?: BackupStatus
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ──────────────────────────────────────────
// App Settings
// ──────────────────────────────────────────

export interface AppSettingsResponse {
  // Email
  emailEnabled: boolean
  emailHost: string | null
  emailPort: number | null
  emailUsername: string | null
  emailFrom: string | null
  emailSsl: boolean
  emailStartTls: boolean
  emailTimeoutMs: number
  emailPasswordConfigured: boolean
  emailConnectionValid: boolean | null

  // Notifications
  notifyOnSuccess: boolean
  notifyOnFailure: boolean
  notificationRecipients: string | null

  // AWS / S3
  awsEnabled: boolean
  awsBucketName: string | null
  awsRegion: string | null
  awsAccessKey: string | null
  awsEndpoint: string | null
  awsPathStyleAccess: boolean
  awsDestinationDirectory: string | null
  awsSecretKeyConfigured: boolean
  awsConnectionValid: boolean | null

  // Heartbeat
  heartbeatEnabled: boolean
  heartbeatUrl: string | null
  heartbeatIntervalSeconds: number
  heartbeatConnectionValid: boolean | null

  // Audit
  updatedAt: string | null
}

export interface EmailCheckRequest {
  host: string
  port: number
  username?: string
  password: string | null
  from?: string
  ssl: boolean
  startTls: boolean
  timeoutMs?: number
}

export interface HeartbeatCheckRequest {
  url: string
}

export interface ConnectionCheckResult {
  reachable: boolean
  errorMessage?: string
}

export interface AwsCheckRequest {
  bucketName: string
  region: string
  accessKey: string
  awsSecretKey: string | null
  endpoint?: string
  pathStyleAccess: boolean
  destinationDirectory?: string
}

export interface BucketCheckResult {
  bucketName: string
  reachable: boolean
  errorMessage?: string
}

export interface AppSettingsRequest {
  // Email
  emailEnabled: boolean
  emailHost?: string
  emailPort?: number
  emailUsername?: string
  /** null = keep existing · '' = clear · 'value' = set new */
  emailPassword?: string | null
  emailFrom?: string
  emailSsl: boolean
  emailStartTls: boolean
  emailTimeoutMs?: number

  // Notifications
  notifyOnSuccess: boolean
  notifyOnFailure: boolean
  notificationRecipients?: string

  // AWS / S3
  awsEnabled: boolean
  awsBucketName?: string
  awsRegion?: string
  awsAccessKey?: string
  /** null = keep existing · '' = clear · 'value' = set new */
  awsSecretKey?: string | null
  awsEndpoint?: string
  awsPathStyleAccess: boolean
  awsDestinationDirectory?: string

  // Heartbeat
  heartbeatEnabled: boolean
  heartbeatUrl?: string
  heartbeatIntervalSeconds?: number
}

package tech.nomad4.backupmanager.backuphistory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistent record of a single backup execution.
 * <p>
 * Intentionally denormalizes task name, socket ID, container ID and database name
 * so that history remains readable even after the originating task is deleted.
 * </p>
 */
@Entity
@Table(name = "backup_records", indexes = {
        @Index(name = "idx_backup_records_task_id",  columnList = "task_id"),
        @Index(name = "idx_backup_records_status",   columnList = "status"),
        @Index(name = "idx_backup_records_started",  columnList = "started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class BackupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the {@code BackupTask} that triggered this run. Nullable when task is deleted. */
    @Column(name = "task_id")
    private Long taskId;

    /** Snapshot of the task name at the time of execution. */
    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    /** Socket used for this backup. */
    @Column(name = "socket_id")
    private Long socketId;

    /** Container targeted by this backup. */
    @Column(name = "container_id", length = 64)
    private String containerId;

    /** Container name targeted by this backup. */
    @Column(name = "container_name", length = 64)
    private String containerName;

    /** Database name backed up. */
    @Column(name = "database_name", length = 255)
    private String databaseName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackupStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Total duration in milliseconds; null while running. */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** Absolute path to the backup file on disk. */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    /** File size in bytes; null if backup failed. */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Error message if status is FAILED or UPLOAD_FAILED; null otherwise. */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** S3 key where the backup file was uploaded; null if upload was not performed or failed. */
    @Column(name = "aws_key", length = 1000)
    private String awsKey;

    /** S3 bucket name used for upload; null if upload was not performed or failed. */
    @Column(name = "aws_bucket_name", length = 255)
    private String awsBucketName;
}

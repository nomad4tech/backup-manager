package tech.nomad4.backupmanager.restore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;

import java.time.LocalDateTime;

@Entity
@Table(name = "restore_records", indexes = {
        @Index(name = "idx_restore_records_backup_record_id", columnList = "backup_record_id"),
        @Index(name = "idx_restore_records_status",           columnList = "status"),
        @Index(name = "idx_restore_records_started",          columnList = "started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RestoreRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the source {@code BackupRecord}. Nullable in case the backup record is later deleted. */
    @Column(name = "backup_record_id")
    private Long backupRecordId;

    /** Snapshot of the backup file path at restore time; stays readable if BackupRecord is deleted. */
    @Column(name = "backup_file_path", length = 1000)
    private String backupFilePath;

    /** Socket used for this restore. */
    @Column(name = "socket_id")
    private Long socketId;

    /** Container targeted by this restore. */
    @Column(name = "container_id", length = 64)
    private String containerId;

    /** Container name targeted by this restore. */
    @Column(name = "container_name", length = 64)
    private String containerName;

    /** Database name from the backup. */
    @Column(name = "source_database_name", length = 255)
    private String sourceDatabaseName;

    /** Database name to restore into; may differ from the source. */
    @Column(name = "target_database_name", length = 255)
    private String targetDatabaseName;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", length = 30)
    private DatabaseType databaseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestoreStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Total duration in milliseconds; null while running. */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** Error message if status is FAILED; null otherwise. */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}

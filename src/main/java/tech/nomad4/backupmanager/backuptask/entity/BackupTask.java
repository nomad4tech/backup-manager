package tech.nomad4.backupmanager.backuptask.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;

import java.time.LocalDateTime;

/**
 * Configuration for a scheduled backup job.
 * <p>
 * The {@code name} field doubles as the directory name under
 * {@code backup.base-directory}, so it is constrained to safe filesystem
 * characters ({@code [a-z0-9_-]+}).
 * </p>
 */
@Entity
@Table(name = "backup_tasks", indexes = {
        @Index(name = "idx_backup_tasks_enabled", columnList = "enabled")
})
@Getter
@Setter
@NoArgsConstructor
public class BackupTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique task name - also used as the directory name for backups.
     * Allowed characters: {@code [a-z0-9_-]}.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** ID of the Docker socket to use for this backup. */
    @Column(name = "socket_id", nullable = false)
    private Long socketId;

    /** Short container ID of the target database container. */
    @Column(name = "container_id", nullable = false, length = 64)
    private String containerId;

    /**
     * Container name (without leading slash) used as a fallback lookup key
     * when the stored {@link #containerId} is no longer valid - e.g. after
     * {@code docker-compose down && docker-compose up} which assigns a new ID.
     * Populated automatically when the task is created or updated.
     */
    @Column(name = "container_name", length = 255)
    private String containerName;

    /** Name of the database to back up. */
    @Column(name = "database_name", nullable = false, length = 255)
    private String databaseName;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false, length = 30)
    private DatabaseType databaseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 10)
    private ScheduleType scheduleType;

    /** Cron expression (e.g. {@code 0 2 * * *}). Required when scheduleType is CRON. */
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    /** Delay between runs in seconds. Required when scheduleType is DELAY. */
    @Column(name = "delay_seconds")
    private Long delaySeconds;

    /**
     * Maximum number of backups to keep for this task.
     * After a successful backup, oldest files exceeding this count are deleted.
     * Null means unlimited.
     */
    @Column(name = "keep_backups_count")
    private Integer keepBackupsCount;

    /** Whether this task is active and should be scheduled. */
    @Column(nullable = false)
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.IDLE;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    /** Next scheduled run time, maintained by the scheduler. */
    @Column(name = "next_scheduled_at")
    private LocalDateTime nextScheduledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

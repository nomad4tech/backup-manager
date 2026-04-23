package tech.nomad4.backupmanager.backuptask.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.backuptask.entity.ScheduleType;

/**
 * Request body for creating or updating a backup task.
 */
@Data
@Schema(description = "Backup task configuration")
public class BackupTaskRequest {

    @NotBlank
    @Pattern(
            regexp = "^[a-z0-9][a-z0-9_-]{0,98}[a-z0-9]$|^[a-z0-9]$",
            message = "Name must contain only lowercase letters, digits, hyphens and underscores, " +
                      "and must start and end with a letter or digit"
    )
    @Schema(
            description = "Unique task name - used as the backup directory name. " +
                    "Allowed: lowercase letters, digits, hyphens, underscores.",
            example = "prod-postgres-daily"
    )
    private String name;

    @Schema(description = "Optional description", example = "Daily backup of production database")
    private String description;

    @NotNull
    @Schema(description = "Docker socket ID to use for this backup", example = "1")
    private Long socketId;

    @NotBlank
    @Schema(description = "Target container ID (short form)", example = "a1b2c3d4e5f6")
    private String containerId;

    @NotBlank
    @Schema(description = "Database name to back up", example = "mydb")
    private String databaseName;

    @NotNull
    @Schema(description = "Database type", example = "POSTGRES")
    private DatabaseType databaseType;

    @NotNull
    @Schema(description = "Schedule type: CRON or DELAY", example = "CRON")
    private ScheduleType scheduleType;

    @Schema(
            description = "Cron expression (required when scheduleType=CRON). Standard 5-field cron.",
            example = "0 2 * * *"
    )
    private String cronExpression;

    @Min(value = 1, message = "delayHours must be between 1 and 168")
    @Max(value = 168, message = "delayHours must be between 1 and 168")
    @Schema(
            description = "Interval between runs in hours (1–168, required when scheduleType=DELAY).",
            example = "6"
    )
    private Integer delayHours;

    @Min(value = 1, message = "keepBackupsCount must be at least 1")
    @Max(value = 9999, message = "keepBackupsCount must not exceed 9999")
    @Schema(
            description = "Maximum number of backup files to keep. Oldest are deleted after each successful backup. " +
                    "Null means unlimited.",
            example = "7"
    )
    private Integer keepBackupsCount;

    @Schema(description = "Whether the task is active. Defaults to true.", example = "true")
    private Boolean enabled = true;

    @Schema(description = "Enable gzip compression for backup files. Defaults to true.", example = "true")
    private Boolean compressionEnabled = true;

    @Schema(description = "Upload backup to S3 after completion (requires AWS enabled in settings). Defaults to true.", example = "true")
    private Boolean uploadToS3 = true;
}

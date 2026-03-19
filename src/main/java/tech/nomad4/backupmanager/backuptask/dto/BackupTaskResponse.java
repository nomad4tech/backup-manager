package tech.nomad4.backupmanager.backuptask.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.backuptask.entity.BackupTask;
import tech.nomad4.backupmanager.backuptask.entity.ScheduleType;
import tech.nomad4.backupmanager.backuptask.entity.TaskStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Backup task configuration and runtime status")
public class BackupTaskResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final Long socketId;
    private final String socketName;
    private final String containerId;
    private final String containerName;
    private final String databaseName;
    private final DatabaseType databaseType;
    private final ScheduleType scheduleType;

    @Schema(description = "Stored cron expression (6-field Spring format)", example = "0 2 * * *")
    private final String cronExpression;

    @Schema(description = "Delay between runs in hours derived from delaySeconds (1–168)", example = "6")
    private final Integer delayHours;

    private final Integer keepBackupsCount;
    private final Boolean enabled;
    private final TaskStatus status;
    private final String lastError;
    private final LocalDateTime nextScheduledAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static BackupTaskResponse from(BackupTask task) {
        return from(task, null);
    }

    public static BackupTaskResponse from(BackupTask task, String socketName) {
        return BackupTaskResponse.builder()
                .id(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .socketId(task.getSocketId())
                .socketName(socketName)
                .containerId(task.getContainerId())
                .containerName(task.getContainerName())
                .databaseName(task.getDatabaseName())
                .databaseType(task.getDatabaseType())
                .scheduleType(task.getScheduleType())
                .cronExpression(task.getCronExpression())
                .delayHours(toDelayHours(task.getDelaySeconds()))
                .keepBackupsCount(task.getKeepBackupsCount())
                .enabled(task.getEnabled())
                .status(task.getStatus())
                .lastError(task.getLastError())
                .nextScheduledAt(task.getNextScheduledAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    /** Identity factory - allows the controller's {@code map(BackupTaskResponse::from)} to
     *  pass through pre-built responses returned by the service without re-mapping. */
    public static BackupTaskResponse from(BackupTaskResponse r) {
        return r;
    }

    /**
     * Extracts the hour field from a stored cron expression of the form
     * {@code "0 H * * *"} (seconds, hour, ...).
     * Returns {@code null} if the expression is absent or not in that form.
     */
    private static Integer parseCronHour(String cronExpression) {
        if (cronExpression == null) return null;
        String[] parts = cronExpression.split(" ");
        if (parts.length < 2) return null;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toDelayHours(Long delaySeconds) {
        if (delaySeconds == null) return null;
        return (int) (delaySeconds / 3600);
    }
}

package tech.nomad4.backupmanager.backuphistory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.backuphistory.entity.BackupRecord;
import tech.nomad4.backupmanager.backuphistory.entity.BackupStatus;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Result of a single backup execution")
public class BackupRecordResponse {

    @Schema(example = "42")
    private final Long id;

    @Schema(description = "ID of the originating backup task (null if task was deleted)", example = "7")
    private final Long taskId;

    @Schema(description = "Task name snapshot", example = "prod-postgres-daily")
    private final String taskName;

    @Schema(example = "1")
    private final Long socketId;

    @Schema(example = "a1b2c3d4e5f6")
    private final String containerId;

    @Schema(example = "my-postgres-container")
    private final String containerName;

    @Schema(example = "mydb")
    private final String databaseName;

    @Schema(example = "POSTGRES")
    private final String databaseType;

    @Schema(example = "SUCCESS")
    private final BackupStatus status;

    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;

    @Schema(description = "Duration in milliseconds", example = "3200")
    private final Long durationMs;

    @Schema(description = "Absolute path to the backup file", example = "/backups/prod-postgres-daily/prod-postgres-daily_20260314_140000.sql")
    private final String filePath;

    @Schema(description = "Path to the backup file relative to the configured base directory", example = "prod-postgres-daily/prod-postgres-daily_20260314_140000.sql")
    private final String relativeFilePath;

    @Schema(description = "File size in bytes", example = "10485760")
    private final Long fileSizeBytes;

    @Schema(description = "Human-readable file size", example = "10.0 MB")
    private final String fileSizeFormatted;

    @Schema(description = "Error message if status is FAILED")
    private final String errorMessage;

    @Schema(description = "S3 key where the backup file was uploaded")
    private final String awsKey;

    @Schema(description = "S3 bucket name used for upload")
    private final String awsBucketName;

    public static BackupRecordResponse from(BackupRecord record, String baseDirectory) {
        return BackupRecordResponse.builder()
                .id(record.getId())
                .taskId(record.getTaskId())
                .taskName(record.getTaskName())
                .socketId(record.getSocketId())
                .containerId(record.getContainerId())
                .containerName(record.getContainerName())
                .databaseName(record.getDatabaseName())
                .databaseType(record.getDatabaseType() != null ? record.getDatabaseType().name() : null)
                .status(record.getStatus())
                .startedAt(record.getStartedAt())
                .completedAt(record.getCompletedAt())
                .durationMs(record.getDurationMs())
                .filePath(record.getFilePath())
                .relativeFilePath(computeRelativePath(record.getFilePath(), baseDirectory))
                .fileSizeBytes(record.getFileSizeBytes())
                .fileSizeFormatted(formatBytes(record.getFileSizeBytes()))
                .errorMessage(record.getErrorMessage())
                .awsKey(record.getAwsKey())
                .awsBucketName(record.getAwsBucketName())
                .build();
    }

    public static BackupRecordResponse from(BackupRecord record) {
        return from(record, null);
    }

    private static String computeRelativePath(String filePath, String baseDirectory) {
        if (filePath == null || filePath.isBlank() || baseDirectory == null) return null;
        try {
            return Path.of(baseDirectory).relativize(Path.of(filePath)).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String formatBytes(Long bytes) {
        if (bytes == null) return null;
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

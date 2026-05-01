package tech.nomad4.backupmanager.restore.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.restore.entity.RestoreRecord;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Result of a single restore execution")
public class RestoreRecordResponse {

    @Schema(example = "1")
    private final Long id;

    @Schema(description = "ID of the source backup record (null if deleted)", example = "42")
    private final Long backupRecordId;

    @Schema(description = "Snapshot of the backup file path at restore time",
            example = "/backups/mydb/mydb_20260501_120000.sql.gz")
    private final String backupFilePath;

    @Schema(example = "1")
    private final Long socketId;

    @Schema(example = "a1b2c3d4e5f6")
    private final String containerId;

    @Schema(example = "my-postgres-container")
    private final String containerName;

    @Schema(description = "Database name from the source backup", example = "mydb")
    private final String sourceDatabaseName;

    @Schema(description = "Database name to restore into", example = "mydb_restored")
    private final String targetDatabaseName;

    @Schema(example = "POSTGRES")
    private final String databaseType;

    @Schema(example = "SUCCESS")
    private final String status;

    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;

    @Schema(description = "Duration in milliseconds", example = "4800")
    private final Long durationMs;

    @Schema(description = "Error message if status is FAILED")
    private final String errorMessage;

    public static RestoreRecordResponse from(RestoreRecord r) {
        return RestoreRecordResponse.builder()
                .id(r.getId())
                .backupRecordId(r.getBackupRecordId())
                .backupFilePath(r.getBackupFilePath())
                .socketId(r.getSocketId())
                .containerId(r.getContainerId())
                .containerName(r.getContainerName())
                .sourceDatabaseName(r.getSourceDatabaseName())
                .targetDatabaseName(r.getTargetDatabaseName())
                .databaseType(r.getDatabaseType() != null ? r.getDatabaseType().name() : null)
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .startedAt(r.getStartedAt())
                .completedAt(r.getCompletedAt())
                .durationMs(r.getDurationMs())
                .errorMessage(r.getErrorMessage())
                .build();
    }
}

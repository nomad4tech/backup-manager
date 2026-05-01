package tech.nomad4.backupmanager.restore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRestoreRequest {

    @NotNull
    private Long backupRecordId;

    @NotNull
    private Long socketId;

    @NotBlank
    private String containerId;

    @NotBlank
    private String containerName;

    @NotBlank
    private String targetDatabaseName;
}

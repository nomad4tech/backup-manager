package tech.nomad4.backupmanager.restore.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Result of a database existence check inside a container")
public class DbExistsResponse {

    @Schema(description = "True if the database was found in the container", example = "true")
    private boolean exists;

    @Schema(example = "mydb")
    private String databaseName;

    @Schema(description = "Human-readable result message")
    private String message;
}

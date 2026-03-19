package tech.nomad4.backupmanager.socketmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response object for Docker connection test and connect operations.
 */
@Data
@AllArgsConstructor
@Schema(description = "Result of a Docker connection test or connect operation")
public class ConnectionTestResponse {

    @Schema(description = "Whether the connection was successful", example = "true")
    private boolean success;

    @Schema(description = "Human-readable result message", example = "Connection successful")
    private String message;

    @Schema(description = "Docker daemon version on the remote host (null on failure)", example = "24.0.7")
    private String dockerVersion;

    @Schema(description = "Number of containers on the remote host (null on failure)", example = "5")
    private Integer containersCount;

    @Schema(description = "Error details when connection fails (null on success)")
    private String error;

    public static ConnectionTestResponse success(String dockerVersion, Integer containersCount) {
        return new ConnectionTestResponse(true, "Connection successful", dockerVersion, containersCount, null);
    }

    public static ConnectionTestResponse error(String errorMessage) {
        return new ConnectionTestResponse(false, "Connection failed", null, null, errorMessage);
    }
}

package tech.nomad4.backupmanager.socketmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request object for creating or updating a Docker socket connection.
 */
@Data
@Schema(description = "Request to create or update an SSH-based Docker socket connection")
public class DockerSocketCreateRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Unique display name for this Docker socket connection",
            example = "production-server", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "SSH host is required")
    @Schema(description = "Hostname or IP address of the remote Docker host",
            example = "192.168.1.100", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sshHost;

    @Min(value = 1, message = "SSH port must be between 1 and 65535")
    @Max(value = 65535, message = "SSH port must be between 1 and 65535")
    @Schema(description = "SSH port on the remote host", example = "22", defaultValue = "22")
    private Integer sshPort = 22;

    @NotBlank(message = "SSH user is required")
    @Schema(description = "SSH username for authentication",
            example = "deploy", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sshUser;

    @Schema(description = "SSH password for authentication. Either password or private key path must be provided.",
            example = "s3cret")
    private String sshPassword;

    @Schema(description = "Absolute path to an SSH private key file on the server running this application.",
            example = "/home/app/.ssh/id_rsa")
    private String sshPrivateKeyPath;

    @Schema(description = "Path to the Docker socket on the remote host",
            example = "/var/run/docker.sock", defaultValue = "/var/run/docker.sock")
    private String remoteDockerSocketPath = "/var/run/docker.sock";

    @Min(value = 1024, message = "Socat port must be between 1024 and 65535")
    @Max(value = 65535, message = "Socat port must be between 1024 and 65535")
    @Schema(description = "TCP port for socat to listen on the remote host.",
            example = "2375", defaultValue = "2375")
    private Integer remoteSocatPort = 2375;
}

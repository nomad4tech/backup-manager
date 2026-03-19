package tech.nomad4.backupmanager.socketmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.nomad4.backupmanager.isolate.socket.model.SocketType;
import tech.nomad4.backupmanager.socketmanagement.entity.ConnectionStatus;
import tech.nomad4.backupmanager.socketmanagement.entity.DockerSocket;
import tech.nomad4.backupmanager.socketmanagement.entity.SocatStatus;

import java.time.LocalDateTime;

/**
 * Response object representing a Docker socket connection and its current status.
 */
@Data
@Schema(description = "Docker socket connection details and current status")
public class DockerSocketResponse {

    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "Display name of the socket connection", example = "production-server")
    private String name;

    @Schema(description = "Connection type", example = "REMOTE_SSH")
    private SocketType type;

    @Schema(description = "Path to the local Docker socket (for LOCAL type)", example = "/var/run/docker.sock")
    private String socketPath;

    @Schema(description = "SSH host address (for REMOTE_SSH type)", example = "192.168.1.100")
    private String sshHost;

    @Schema(description = "SSH port (for REMOTE_SSH type)", example = "22")
    private Integer sshPort;

    @Schema(description = "SSH username (for REMOTE_SSH type)", example = "deploy")
    private String sshUser;

    @Schema(description = "Docker socket path on the remote host", example = "/var/run/docker.sock")
    private String remoteDockerSocketPath;

    @Schema(description = "TCP port used by socat on the remote host", example = "2375")
    private Integer remoteSocatPort;

    @Schema(description = "Current connection status", example = "CONNECTED")
    private ConnectionStatus status;

    @Schema(description = "Timestamp of the last successful connection")
    private LocalDateTime lastConnected;

    @Schema(description = "Last error message, if any", example = "Connection refused")
    private String lastError;

    @Schema(description = "Timestamp when this socket was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp of the last configuration update")
    private LocalDateTime updatedAt;

    @Schema(description = "Current socat relay status", example = "MANAGED_BY_US")
    private SocatStatus socatStatus;

    @Schema(description = "Human-readable description of the socat relay state")
    private String socatInfo;

    @Schema(description = "Whether this socket is system-managed and protected from modification", example = "false")
    private Boolean isSystem;

    public static DockerSocketResponse from(DockerSocket socket) {
        DockerSocketResponse response = new DockerSocketResponse();
        response.setId(socket.getId());
        response.setName(socket.getName());
        response.setType(socket.getType());
        response.setSocketPath(socket.getSocketPath());
        response.setSshHost(socket.getSshHost());
        response.setSshPort(socket.getSshPort());
        response.setSshUser(socket.getSshUser());
        response.setRemoteDockerSocketPath(socket.getRemoteDockerSocketPath());
        response.setRemoteSocatPort(socket.getRemoteSocatPort());
        response.setStatus(socket.getStatus());
        response.setLastConnected(socket.getLastConnected());
        response.setLastError(socket.getLastError());
        response.setCreatedAt(socket.getCreatedAt());
        response.setUpdatedAt(socket.getUpdatedAt());
        response.setIsSystem(socket.getIsSystem());

        if (socket.getType() == SocketType.LOCAL) {
            response.setSocatStatus(SocatStatus.NOT_NEEDED);
            response.setSocatInfo(Boolean.TRUE.equals(socket.getIsSystem())
                    ? "System local socket (managed automatically)"
                    : "Local socket - socat not needed");
        } else if (Boolean.TRUE.equals(socket.getSocatManagedByUs())) {
            response.setSocatStatus(SocatStatus.MANAGED_BY_US);
            response.setSocatInfo(String.format(
                    "socat managed by application (PID: %d, port: %d)",
                    socket.getRemoteSocatPid(), socket.getRemoteSocatPort()
            ));
        } else if (socket.getStatus() == ConnectionStatus.CONNECTED) {
            response.setSocatStatus(SocatStatus.EXTERNAL);
            response.setSocatInfo(String.format(
                    "Using existing Docker TCP on port %d (external)", socket.getRemoteSocatPort()
            ));
        } else {
            response.setSocatStatus(SocatStatus.NOT_RUNNING);
            response.setSocatInfo("Not connected");
        }

        return response;
    }
}

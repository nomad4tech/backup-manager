package tech.nomad4.backupmanager.isolate.socket.model;

import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.isolate.socket.service.DockerSocketService;

/**
 * Value object containing all parameters needed to establish a Docker socket connection.
 * <p>
 * Passed by the application layer to {@link DockerSocketService}
 * so that the core socket package has no dependency on the database or JPA.
 * </p>
 */
@Getter
@Builder
public class DockerSocketConfig {

    private final Long id;
    private final SocketType type;

    /** Path to the local Docker Unix socket. Used for {@link SocketType#LOCAL} connections. */
    private final String socketPath;

    // --- SSH connection settings (for REMOTE_SSH) ---

    private final String sshHost;
    private final Integer sshPort;
    private final String sshUser;
    private final String sshPassword;
    private final String sshPrivateKeyPath;

    /** Path to the Docker Unix socket on the remote host. */
    private final String remoteDockerSocketPath;

    /** TCP port that socat listens on for the remote host. */
    private final Integer remoteSocatPort;
}

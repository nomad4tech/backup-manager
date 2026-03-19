package tech.nomad4.backupmanager.socketmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tech.nomad4.backupmanager.isolate.socket.model.SocketType;

import java.time.LocalDateTime;

/**
 * JPA entity representing a Docker socket connection configuration.
 * <p>
 * Stores all configuration needed to connect to a Docker daemon, either locally
 * via Unix socket or remotely via SSH tunneling with socat relay. Also tracks
 * connection state, socat management status, and audit timestamps.
 * </p>
 *
 * <p>System sockets (created automatically on startup) are protected from
 * modification and deletion through the API.</p>
 *
 * <p><strong>Security note:</strong> The {@code sshPassword} field is stored
 * in plain text. Implement encryption (e.g., Jasypt) before production use,
 * or prefer private key authentication.</p>
 */
@Entity
@Table(name = "docker_sockets")
@Getter
@Setter
@NoArgsConstructor
public class DockerSocket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocketType type;

    /** Path to the local Docker Unix socket. Used for LOCAL type connections. */
    @Column(name = "socket_path", length = 255)
    private String socketPath = "/var/run/docker.sock";

    // --- SSH connection settings (for REMOTE_SSH type) ---

    @Column(name = "ssh_host", length = 255)
    private String sshHost;

    @Column(name = "ssh_port")
    private Integer sshPort = 22;

    @Column(name = "ssh_user", length = 100)
    private String sshUser;

    /**
     * SSH password for authentication.
     * <p><strong>Warning:</strong> Stored in plain text. Use private key
     * authentication or implement encryption for production deployments.</p>
     */
    @Column(name = "ssh_password", length = 255)
    private String sshPassword;

    @Column(name = "ssh_private_key_path", length = 500)
    private String sshPrivateKeyPath;

    /** Path to the Docker Unix socket on the remote host. */
    @Column(name = "remote_docker_socket_path", length = 255)
    private String remoteDockerSocketPath = "/var/run/docker.sock";

    // --- Socat relay settings (for SSH connections) ---

    /** TCP port that socat listens on for the remote host. */
    @Column(name = "remote_socat_port")
    private Integer remoteSocatPort = 2375;

    /** Whether the socat process was started and is managed by this application. */
    @Column(name = "socat_managed_by_us")
    private Boolean socatManagedByUs = false;

    /** PID of the socat process on the remote host, if managed by this application. */
    @Column(name = "remote_socat_pid")
    private Integer remoteSocatPid;

    /** Whether this is a system-managed socket, protected from API modification. */
    @Column(name = "is_system")
    private Boolean isSystem = false;

    // --- Connection state tracking ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    @Column(name = "last_connected")
    private LocalDateTime lastConnected;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    // --- Audit fields ---

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

package tech.nomad4.backupmanager.socketmanagement.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.nomad4.dockersocketmanager.model.SocketType;
import tech.nomad4.dockersocketmanager.service.DockerSocketService;
import tech.nomad4.backupmanager.socketmanagement.dto.ConnectionTestResponse;
import tech.nomad4.backupmanager.socketmanagement.dto.DockerSocketCreateRequest;
import tech.nomad4.backupmanager.socketmanagement.dto.DockerSocketResponse;
import tech.nomad4.backupmanager.socketmanagement.entity.ConnectionStatus;
import tech.nomad4.backupmanager.socketmanagement.entity.DockerSocket;
import tech.nomad4.backupmanager.socketmanagement.exception.SocketNotFoundException;
import tech.nomad4.backupmanager.socketmanagement.repository.DockerSocketRepository;
import tech.nomad4.dockersocketmanager.exception.DockerConnectionException;
import tech.nomad4.dockersocketmanager.model.DockerSocketConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Application-layer facade that bridges the database (JPA) and the core
 * {@link DockerSocketService}.
 * <p>
 * Responsible for:
 * <ul>
 *   <li>CRUD operations on {@link DockerSocket} entities</li>
 *   <li>Translating DB entities to {@link DockerSocketConfig} value objects</li>
 *   <li>Persisting connection status and socat metadata</li>
 *   <li>Delegating actual socket connection/disconnection to the core service</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerSocketFacadeService {

    private final DockerSocketService socketService;
    private final DockerSocketRepository repository;

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    public List<DockerSocketResponse> getAllSockets() {
        return repository.findAll().stream()
                .map(DockerSocketResponse::from)
                .collect(Collectors.toList());
    }

    public DockerSocketResponse getSocket(Long id) {
        return DockerSocketResponse.from(
                repository.findById(id).orElseThrow(() -> new SocketNotFoundException(id))
        );
    }

    @Transactional
    public DockerSocketResponse createSocket(DockerSocketCreateRequest request) {
        if (repository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Socket with name '" + request.getName() + "' already exists");
        }

        DockerSocket socket = new DockerSocket();
        socket.setName(request.getName());
        socket.setType(SocketType.REMOTE_SSH);
        applyRequestToSocket(request, socket);

        DockerSocket saved = repository.save(socket);
        log.info("Created new Docker socket: {} ({})", saved.getName(), saved.getType());
        return DockerSocketResponse.from(saved);
    }

    @Transactional
    public DockerSocketResponse updateSocket(Long id, DockerSocketCreateRequest request) {
        DockerSocket socket = repository.findById(id)
                .orElseThrow(() -> new SocketNotFoundException(id));

        if (Boolean.TRUE.equals(socket.getIsSystem())) {
            throw new IllegalArgumentException("Cannot modify system socket");
        }

        if (!socket.getName().equals(request.getName()) && repository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Socket with name '" + request.getName() + "' already exists");
        }

        socket.setName(request.getName());
        applyRequestToSocket(request, socket);
        DockerSocket updated = repository.save(socket);
        log.info("Updated Docker socket: {}", updated.getName());

        // Disconnect so the next getClient() call picks up the new config
        disconnect(id);

        return DockerSocketResponse.from(updated);
    }

    @Transactional
    public void deleteSocket(Long id) {
        DockerSocket socket = repository.findById(id)
                .orElseThrow(() -> new SocketNotFoundException(id));

        if (Boolean.TRUE.equals(socket.getIsSystem())) {
            throw new IllegalArgumentException("Cannot delete system socket. It is managed automatically.");
        }

        disconnect(id);
        repository.deleteById(id);
        log.info("Deleted Docker socket: {}", id);
    }

    // -------------------------------------------------------------------------
    // Connection lifecycle
    // -------------------------------------------------------------------------

    /**
     * Returns a ready Docker client for the given socket, reusing an active connection
     * or creating a new one using the stored configuration.
     * <p>
     * Intended for internal use by orchestration components (dev runners, OrchestratorService).
     * Does not update DB connection status - use {@link #connect(Long)} for that.
     * </p>
     *
     * @param socketId the ID of the Docker socket
     * @return a connected Docker client
     */
    public DockerClient getDockerClient(Long socketId) {
        DockerSocket socket = repository.findById(socketId)
                .orElseThrow(() -> new SocketNotFoundException(socketId));
        return socketService.getClient(socketId, toConfig(socket));
    }

    /**
     * Establishes a connection and returns Docker host information.
     *
     * @throws DockerConnectionException if the connection cannot be established
     */
    @Transactional
    public ConnectionTestResponse connect(Long id) {
        DockerSocket socket = repository.findById(id)
                .orElseThrow(() -> new SocketNotFoundException(id));

        updateSocketStatus(socket, ConnectionStatus.CONNECTING, null);

        try {
            DockerClient client = socketService.getClient(id, toConfig(socket));

            // Persist socat metadata for display
            socketService.getSocatResult(id).ifPresent(socat -> {
                socket.setSocatManagedByUs(socat.isManagedByUs());
                socket.setRemoteSocatPid(socat.getPid());
            });

            updateSocketStatus(socket, ConnectionStatus.CONNECTED, null);
            socket.setLastConnected(LocalDateTime.now());
            repository.save(socket);

            Info info = client.infoCmd().exec();
            return ConnectionTestResponse.success(info.getServerVersion(), info.getContainers());

        } catch (Exception e) {
            log.error("Connection failed for socket {}: {}", id, e.getMessage());
            updateSocketStatus(socket, ConnectionStatus.ERROR, e.getMessage());
            throw new DockerConnectionException("Failed to connect to socket: " + socket.getName(), e);
        }
    }

    /**
     * Disconnects an active connection and updates the DB status.
     */
    @Transactional
    public void disconnect(Long id) {
        socketService.disconnect(id);
        repository.findById(id).ifPresent(socket -> {
            socket.setStatus(ConnectionStatus.DISCONNECTED);
            socket.setLastError(null);
            socket.setSocatManagedByUs(false);
            socket.setRemoteSocatPid(null);
            repository.save(socket);
        });
        log.info("Disconnected socket {}", id);
    }

    /**
     * Tests connectivity by pinging the Docker daemon and returning host info.
     *
     * @throws DockerConnectionException if the connection test fails
     */
    public ConnectionTestResponse testConnection(Long id) {
        DockerSocket socket = repository.findById(id)
                .orElseThrow(() -> new SocketNotFoundException(id));
        DockerClient client = socketService.getClient(id, toConfig(socket));
        client.pingCmd().exec();
        Info info = client.infoCmd().exec();
        return ConnectionTestResponse.success(info.getServerVersion(), info.getContainers());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateSocketStatus(DockerSocket socket, ConnectionStatus status, String error) {
        socket.setStatus(status);
        socket.setLastError(error);
        repository.save(socket);
    }

    private DockerSocketConfig toConfig(DockerSocket socket) {
        return DockerSocketConfig.builder()
                .id(socket.getId())
                .type(socket.getType())
                .socketPath(socket.getSocketPath())
                .sshHost(socket.getSshHost())
                .sshPort(socket.getSshPort())
                .sshUser(socket.getSshUser())
                .sshPassword(socket.getSshPassword())
                .sshPrivateKeyPath(socket.getSshPrivateKeyPath())
                .remoteDockerSocketPath(socket.getRemoteDockerSocketPath())
                .remoteSocatPort(socket.getRemoteSocatPort())
                .build();
    }

    private void applyRequestToSocket(DockerSocketCreateRequest request, DockerSocket socket) {
        socket.setSshHost(request.getSshHost());
        socket.setSshPort(request.getSshPort());
        socket.setSshUser(request.getSshUser());
        socket.setSshPassword(request.getSshPassword());
        socket.setSshPrivateKeyPath(request.getSshPrivateKeyPath());
        socket.setRemoteDockerSocketPath(request.getRemoteDockerSocketPath());
        socket.setRemoteSocatPort(request.getRemoteSocatPort());
    }
}

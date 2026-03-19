package tech.nomad4.backupmanager.socketmanagement.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tech.nomad4.dockersocketmanager.model.SocketType;
import tech.nomad4.dockersocketmanager.util.DockerEnvironmentDetector;
import tech.nomad4.backupmanager.socketmanagement.entity.DockerSocket;
import tech.nomad4.backupmanager.socketmanagement.repository.DockerSocketRepository;

import java.util.Optional;

/**
 * Initializes the system-managed local Docker socket on application startup.
 * <p>
 * Detects the runtime environment, checks for Docker socket availability, and
 * creates a protected system socket entry in the database if one does not exist.
 * </p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LocalSocketInitializer implements CommandLineRunner {

    private final DockerSocketRepository repository;

    private static final String LOCAL_SOCKET_NAME = "local-docker";
    private static final String DEFAULT_SOCKET_PATH = "/var/run/docker.sock";

    @Override
    public void run(String... args) {
        log.info("=== Initializing Docker Socket Service ===");
        ensureLocalSocketExists();
    }

    public void ensureLocalSocketExists() {
        boolean inDocker = DockerEnvironmentDetector.isRunningInDocker();
        boolean socketAvailable = DockerEnvironmentDetector.isDockerSocketAvailable();

        log.info("Environment: running in Docker={}, Docker socket available={}", inDocker, socketAvailable);

        Optional<DockerSocket> existing = repository.findByIsSystemTrue();
        if (existing.isPresent()) {
            log.info("System local Docker socket already exists: {} (id={})",
                    existing.get().getName(), existing.get().getId());
            return;
        }

        if (!socketAvailable) {
            log.warn("Docker socket not available at {}", DEFAULT_SOCKET_PATH);
            log.warn("{}", DockerEnvironmentDetector.getSetupRecommendations());
            log.warn("System local socket will not be created. Use SSH sockets via API instead.");
            return;
        }

        try {
            DockerSocket localSocket = new DockerSocket();
            localSocket.setName(LOCAL_SOCKET_NAME);
            localSocket.setType(SocketType.LOCAL);
            localSocket.setSocketPath(DEFAULT_SOCKET_PATH);
            localSocket.setIsSystem(true);

            repository.save(localSocket);

            log.info("System local Docker socket created: name={}, path={}, id={}",
                    LOCAL_SOCKET_NAME, DEFAULT_SOCKET_PATH, localSocket.getId());
        } catch (Exception e) {
            log.error("Failed to create system local Docker socket: {}", e.getMessage(), e);
        }
    }
}

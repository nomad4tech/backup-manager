package tech.nomad4.backupmanager.socketmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.nomad4.dockersocketmanager.service.DockerSocketService;
import tech.nomad4.backupmanager.socketmanagement.entity.ConnectionStatus;
import tech.nomad4.backupmanager.socketmanagement.entity.DockerSocket;
import tech.nomad4.backupmanager.socketmanagement.repository.DockerSocketRepository;

import java.util.List;

/**
 * Periodically checks the health of all DB-tracked active connections.
 * <p>
 * Queries all sockets with status {@code CONNECTED}, verifies each via the
 * core {@link DockerSocketService}, evicts dead connections from the pool,
 * and updates the database status accordingly.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocketHealthCheckService {

    private final DockerSocketService socketService;
    private final DockerSocketRepository repository;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void checkConnections() {
        List<DockerSocket> connected = repository.findByStatus(ConnectionStatus.CONNECTED);
        if (connected.isEmpty()) {
            return;
        }

        for (DockerSocket socket : connected) {
            if (!socketService.isAlive(socket.getId())) {
                log.warn("Connection {} ({}) is dead, removing from pool", socket.getId(), socket.getName());
                socketService.evict(socket.getId());
                socket.setStatus(ConnectionStatus.DISCONNECTED);
                socket.setLastError("Connection lost during health check");
                socket.setSocatManagedByUs(false);
                socket.setRemoteSocatPid(null);
                repository.save(socket);
            }
        }

        log.debug("Health check complete: {} connections checked", connected.size());
    }
}

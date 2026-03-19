package tech.nomad4.backupmanager.isolate.socket.connection;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Docker connection via a local Unix socket.
 * <p>
 * Connects directly to the Docker daemon through a Unix domain socket
 * (typically {@code /var/run/docker.sock}). Used for the system-managed
 * local Docker socket.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class LocalDockerConnection implements DockerConnection {

    private final Long socketId;
    private final DockerClient client;

    @Override
    public Long getSocketId() {
        return socketId;
    }

    @Override
    public DockerClient getClient() {
        return client;
    }

    @Override
    public boolean isAlive() {
        try {
            client.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.debug("Ping failed for local socket {}: {}", socketId, e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            client.close();
            log.info("Closed local Docker connection for socket {}", socketId);
        } catch (IOException e) {
            log.error("Error closing Docker client for socket {}", socketId, e);
        }
    }
}

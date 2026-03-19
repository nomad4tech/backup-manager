package tech.nomad4.backupmanager.isolate.socket.connection;

import com.github.dockerjava.api.DockerClient;

/**
 * Abstraction for a managed connection to a Docker daemon.
 * <p>
 * Implementations encapsulate the connection resources (Docker client, SSH tunnel, etc.)
 * and provide lifecycle management (health checks, cleanup).
 * </p>
 *
 * @see LocalDockerConnection
 * @see SSHDockerConnection
 */
public interface DockerConnection {

    /**
     * Returns the socket ID associated with this connection (used as the pool key).
     *
     * @return the socket ID
     */
    Long getSocketId();

    /**
     * Returns the Docker client for interacting with the connected Docker daemon.
     *
     * @return the Docker client instance
     */
    DockerClient getClient();

    /**
     * Checks whether the connection is still alive by pinging the Docker daemon.
     *
     * @return {@code true} if the connection is healthy and responsive
     */
    boolean isAlive();

    /**
     * Closes the connection and releases all associated resources.
     * <p>
     * This includes closing the Docker client and, for SSH connections,
     * closing the SSH tunnel and disconnecting the SSH client.
     * </p>
     */
    void close();
}

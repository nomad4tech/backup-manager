package tech.nomad4.backupmanager.isolate.socket.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import tech.nomad4.backupmanager.isolate.socket.connection.DockerConnection;
import tech.nomad4.backupmanager.isolate.socket.connection.LocalDockerConnection;
import tech.nomad4.backupmanager.isolate.socket.connection.SSHDockerConnection;
import tech.nomad4.backupmanager.isolate.socket.connection.SSHTunnel;
import tech.nomad4.backupmanager.isolate.socket.exception.DockerConnectionException;
import tech.nomad4.backupmanager.isolate.socket.model.DockerSocketConfig;
import tech.nomad4.backupmanager.isolate.socket.model.SocketType;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Core service for managing Docker socket connections.
 * <p>
 * Pure connection provider - has no dependency on the database or REST layer.
 * Accepts {@link DockerSocketConfig} value objects from the application layer
 * and maintains an in-memory pool of active {@link DockerConnection}s.
 * </p>
 *
 * <p>Status persistence and health-check scheduling are the responsibility
 * of the application layer (e.g., {@code SocketHealthCheckService}).</p>
 *
 * <p><strong>Security note:</strong> SSH host key verification is currently
 * disabled via {@code PromiscuousVerifier}. Use known-hosts verification in production.</p>
 */
@Slf4j
public class DockerSocketService implements AutoCloseable {

    private final Map<Long, DockerConnection> activeConnections = new ConcurrentHashMap<>();

    /**
     * Returns a ready Docker client for the given socket ID.
     * <p>
     * Reuses an existing connection if alive; otherwise creates a new one using
     * the provided config.
     * </p>
     *
     * @param id     the socket ID used as the pool key
     * @param config connection parameters
     * @return a connected Docker client
     * @throws DockerConnectionException if the connection cannot be established
     */
    public DockerClient getClient(Long id, DockerSocketConfig config) {
        DockerConnection connection = activeConnections.get(id);
        if (connection == null || !connection.isAlive()) {
            log.info("Creating new connection for socket {}", id);
            connection = connect(id, config);
            activeConnections.put(id, connection);
        }
        return connection.getClient();
    }

    /**
     * Returns the socat setup result for an active SSH connection, if available.
     * <p>
     * The application layer can use this to persist socat metadata (e.g., PID) for display.
     * </p>
     *
     * @param id the socket ID
     * @return socat result if the connection is an SSH connection, otherwise empty
     */
    public Optional<SocatSetupResult> getSocatResult(Long id) {
        DockerConnection connection = activeConnections.get(id);
        if (connection instanceof SSHDockerConnection ssh) {
            return Optional.ofNullable(ssh.getSocatResult());
        }
        return Optional.empty();
    }

    /**
     * Returns whether an active connection for the given ID is currently alive.
     *
     * @param id the socket ID
     * @return {@code true} if a connection exists in the pool and responds to ping
     */
    public boolean isAlive(Long id) {
        DockerConnection conn = activeConnections.get(id);
        return conn != null && conn.isAlive();
    }

    /**
     * Removes a dead connection from the pool and closes it.
     * <p>
     * Intended to be called by the application-layer health check after
     * detecting a dead connection via {@link #isAlive(Long)}.
     * </p>
     *
     * @param id the socket ID to evict
     */
    public void evict(Long id) {
        DockerConnection conn = activeConnections.remove(id);
        if (conn != null) {
            conn.close();
        }
    }

    /**
     * Disconnects and removes the active connection for the given socket ID.
     * <p>
     * For SSH connections, also stops any socat process that was started by this application.
     * </p>
     *
     * @param socketId the ID of the socket to disconnect
     */
    public void disconnect(Long socketId) {
        DockerConnection connection = activeConnections.remove(socketId);
        if (connection != null) {
            connection.close();
            log.info("Disconnected socket {}", socketId);
        }
    }

    /**
     * Closes all active Docker connections during application shutdown.
     */
    @Override
    public void close() {
        disconnectAll();
    }

    public void disconnectAll() {
        log.info("Closing all Docker connections ({} active)", activeConnections.size());
        new HashSet<>(activeConnections.keySet()).forEach(this::disconnect);
        activeConnections.clear();
    }

    // -------------------------------------------------------------------------
    // Connection setup
    // -------------------------------------------------------------------------

    private DockerConnection connect(Long id, DockerSocketConfig config) {
        try {
            if (config.getType() == SocketType.LOCAL) {
                return connectLocal(id, config);
            } else {
                return connectRemoteSSH(id, config);
            }
        } catch (DockerConnectionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to connect socket {}: {}", id, e.getMessage());
            throw new DockerConnectionException("Failed to connect socket " + id, e);
        }
    }

    private DockerConnection connectLocal(Long id, DockerSocketConfig config) {
        try {
            String dockerHost = "unix://" + config.getSocketPath();
            DockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();
            DockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                    .dockerHost(clientConfig.getDockerHost())
                    .build();
            DockerClient client = DockerClientImpl.getInstance(clientConfig, httpClient);
            client.pingCmd().exec();
            return new LocalDockerConnection(id, client);
        } catch (Exception e) {
            throw new DockerConnectionException("Failed to connect to local socket", e);
        }
    }

    /**
     * Connects to a remote Docker host via SSH tunnel with socat relay.
     * <ol>
     *   <li>Establish SSH connection</li>
     *   <li>Ensure socat is running on the remote host</li>
     *   <li>Create a local SSH port-forward tunnel</li>
     *   <li>Connect Docker client through the tunnel</li>
     * </ol>
     */
    private DockerConnection connectRemoteSSH(Long id, DockerSocketConfig config) {
        SSHClient sshClient = null;
        SSHTunnel tunnel = null;
        try {
            sshClient = connectSSH(config);
            SocatSetupResult socatResult = ensureSocatRunning(sshClient, config);
            log.info("Socat status: {}", socatResult.getDescription());

            int localPort = findFreePort();
            tunnel = new SSHTunnel(sshClient, localPort, "127.0.0.1", socatResult.getPort());

            DockerClient client = createDockerClient("tcp://localhost:" + localPort);
            client.pingCmd().exec();

            log.info("Docker client connected via SSH tunnel on local port {}", localPort);
            return new SSHDockerConnection(id, client, tunnel, socatResult);

        } catch (Exception e) {
            log.error("SSH connection setup failed: {}", e.getMessage(), e);
            if (tunnel != null) {
                try { tunnel.close(); } catch (Exception ex) {
                    log.debug("Error closing tunnel during cleanup: {}", ex.getMessage());
                }
            } else if (sshClient != null && sshClient.isConnected()) {
                try { sshClient.disconnect(); } catch (IOException ex) {
                    log.debug("Error disconnecting SSH during cleanup: {}", ex.getMessage());
                }
            }
            throw new DockerConnectionException("Failed to connect via SSH: " + e.getMessage(), e);
        }
    }

    /**
     * Establishes an SSH connection to the remote host.
     *
     * <p><strong>Warning:</strong> Host key verification is currently disabled.</p>
     */
    private SSHClient connectSSH(DockerSocketConfig config) throws IOException {
        SSHClient sshClient = new SSHClient();
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());
        sshClient.connect(config.getSshHost(), config.getSshPort());
        log.info("SSH connected to {}:{}", config.getSshHost(), config.getSshPort());

        if (config.getSshPrivateKeyPath() != null && !config.getSshPrivateKeyPath().isEmpty()) {
            KeyProvider keyProvider = sshClient.loadKeys(config.getSshPrivateKeyPath());
            sshClient.authPublickey(config.getSshUser(), keyProvider);
            log.info("SSH authenticated with private key");
        } else if (config.getSshPassword() != null && !config.getSshPassword().isEmpty()) {
            sshClient.authPassword(config.getSshUser(), config.getSshPassword());
            log.info("SSH authenticated with password");
        } else {
            throw new DockerConnectionException("No SSH authentication method provided");
        }
        return sshClient;
    }

    private SocatSetupResult ensureSocatRunning(SSHClient ssh, DockerSocketConfig config) throws IOException {
        int targetPort = config.getRemoteSocatPort();
        String dockerSocket = config.getRemoteDockerSocketPath();

        log.info("Checking socat on port {}...", targetPort);

        if (isPortOccupied(ssh, targetPort)) {
            log.info("Port {} is occupied, checking if it serves Docker API...", targetPort);
            if (isDockerAccessible(ssh, targetPort)) {
                log.info("Port {} already forwarding Docker socket (external setup)", targetPort);
                return SocatSetupResult.external(targetPort);
            } else {
                throw new DockerConnectionException(
                        "Port " + targetPort + " is occupied but not forwarding Docker socket"
                );
            }
        }

        log.info("Port {} is free, starting socat", targetPort);
        if (!isSocatInstalled(ssh)) {
            throw new DockerConnectionException(
                    "socat is not installed on remote server. Install: sudo apt-get install socat"
            );
        }

        log.info("Starting socat: 127.0.0.1:{} -> {}", targetPort, dockerSocket);
        int pid = startSocat(ssh, targetPort, dockerSocket);
        log.info("socat started successfully (PID: {}, port: {} localhost only)", pid, targetPort);
        return SocatSetupResult.managedByUs(targetPort, pid);
    }

    private boolean isPortOccupied(SSHClient ssh, int port) throws IOException {
        Session session = ssh.startSession();
        try {
            Command cmd = session.exec(
                    "netstat -tln 2>/dev/null | grep -q ':" + port + " ' && echo 'OCCUPIED' || " +
                            "ss -tln 2>/dev/null | grep -q ':" + port + " ' && echo 'OCCUPIED' || " +
                            "echo 'FREE'"
            );
            cmd.join(5, TimeUnit.SECONDS);
            String output = IOUtils.readFully(cmd.getInputStream()).toString().trim();
            boolean occupied = output.contains("OCCUPIED");
            log.debug("Port {} status: {}", port, occupied ? "OCCUPIED" : "FREE");
            return occupied;
        } finally {
            session.close();
        }
    }

    private boolean isDockerAccessible(SSHClient ssh, int port) throws IOException {
        Session session = ssh.startSession();
        try {
            Command cmd = session.exec(
                    "timeout 2 curl -s http://127.0.0.1:" + port + "/version 2>/dev/null | grep -q ApiVersion && echo 'OK' || echo 'FAIL'"
            );
            cmd.join(5, TimeUnit.SECONDS);
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            return output.contains("OK");
        } finally {
            session.close();
        }
    }

    private boolean isSocatInstalled(SSHClient ssh) throws IOException {
        Session session = ssh.startSession();
        try {
            Command cmd = session.exec("which socat");
            cmd.join(5, TimeUnit.SECONDS);
            return cmd.getExitStatus() == 0;
        } finally {
            session.close();
        }
    }

    private int startSocat(SSHClient ssh, int port, String socketPath) throws IOException {
        try {
            String cmd = String.format(
                    "nohup socat TCP-LISTEN:%d,bind=127.0.0.1,reuseaddr,fork UNIX-CONNECT:%s " +
                            "> /tmp/socat-docker-%d.log 2>&1 & echo $!",
                    port, socketPath, port
            );
            log.debug("Executing: {}", cmd);

            Session startSession = ssh.startSession();
            String pidStr;
            int pid;
            try {
                Command startCmd = startSession.exec(cmd);
                startCmd.join(5, TimeUnit.SECONDS);
                pidStr = IOUtils.readFully(startCmd.getInputStream()).toString().trim();
                log.debug("socat PID output: '{}'", pidStr);
                pid = Integer.parseInt(pidStr);
            } finally {
                startSession.close();
            }

            Thread.sleep(1000);

            Session checkSession = ssh.startSession();
            try {
                Command checkCmd = checkSession.exec("kill -0 " + pid + " 2>/dev/null && echo 'ALIVE'");
                checkCmd.join(5, TimeUnit.SECONDS);
                String status = IOUtils.readFully(checkCmd.getInputStream()).toString();

                if (!status.contains("ALIVE")) {
                    Session logSession = ssh.startSession();
                    try {
                        Command logCmd = logSession.exec("cat /tmp/socat-docker-" + port + ".log 2>/dev/null || echo 'No log'");
                        logCmd.join(5, TimeUnit.SECONDS);
                        String logOutput = IOUtils.readFully(logCmd.getInputStream()).toString();
                        throw new IOException("socat process died immediately after start. Log: " + logOutput);
                    } finally {
                        logSession.close();
                    }
                }
                return pid;
            } finally {
                checkSession.close();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while starting socat", e);
        } catch (NumberFormatException e) {
            throw new IOException("Failed to parse socat PID from remote output", e);
        }
    }

    private DockerClient createDockerClient(String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        DockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    private int findFreePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}

package tech.nomad4.backupmanager.isolate.socket.connection;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Parameters;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Manages an SSH local port forwarding tunnel.
 * <p>
 * Creates a TCP-to-TCP tunnel through an SSH connection, binding a local port
 * that forwards traffic to a specified port on the remote host (typically
 * the socat relay port). The tunnel runs in a dedicated daemon thread.
 * </p>
 *
 * <p>Typical usage flow:</p>
 * <ol>
 *   <li>SSH client connects to the remote host</li>
 *   <li>This tunnel binds a local port and forwards traffic to the remote socat port</li>
 *   <li>A Docker client connects to the local tunnel port</li>
 *   <li>Traffic flows: Docker client -> local port -> SSH -> remote socat -> Docker socket</li>
 * </ol>
 *
 * @see SSHDockerConnection
 */
@Slf4j
@Getter
public class SSHTunnel {

    private final SSHClient sshClient;
    private final int localPort;
    private final LocalPortForwarder forwarder;
    private final Thread forwarderThread;
    private volatile boolean running = false;

    /**
     * Creates and starts an SSH tunnel for local port forwarding.
     *
     * @param sshClient  an authenticated SSH client
     * @param localPort  the local port to bind for tunnel ingress
     * @param remoteHost the target host on the remote side (typically "127.0.0.1")
     * @param remotePort the target port on the remote side (socat listening port)
     * @throws IOException if the tunnel cannot be established
     */
    public SSHTunnel(SSHClient sshClient, int localPort, String remoteHost, int remotePort) throws IOException {
        this.sshClient = sshClient;
        this.localPort = localPort;

        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("127.0.0.1", localPort));

        Parameters params = new Parameters(
                "127.0.0.1",
                localPort,
                remoteHost,
                remotePort
        );

        this.forwarder = sshClient.newLocalPortForwarder(params, serverSocket);

        this.forwarderThread = new Thread(() -> {
            try {
                running = true;
                log.info("SSH tunnel started: localhost:{} -> {}:{}", localPort, remoteHost, remotePort);
                forwarder.listen();
            } catch (IOException e) {
                if (running) {
                    log.error("SSH tunnel error: {}", e.getMessage());
                }
            } finally {
                running = false;
                log.debug("SSH tunnel forwarder stopped");
            }
        }, "ssh-tunnel-" + localPort);

        this.forwarderThread.setDaemon(true);
        this.forwarderThread.start();

        // Allow time for the tunnel to initialize
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks whether the SSH tunnel is active and the underlying SSH connection is alive.
     *
     * @return {@code true} if the tunnel is running and the SSH connection is established
     */
    public boolean isActive() {
        return running &&
                sshClient != null &&
                sshClient.isConnected() &&
                forwarder != null &&
                forwarder.isRunning();
    }

    /**
     * Closes the SSH tunnel and disconnects the SSH client.
     * <p>
     * Stops the port forwarder, disconnects the SSH session, and interrupts
     * the forwarder thread if it is still running. Waits up to 1 second
     * for the thread to terminate.
     * </p>
     */
    public void close() {
        running = false;

        try {
            if (forwarder != null) {
                forwarder.close();
            }
        } catch (IOException e) {
            log.error("Error closing port forwarder: {}", e.getMessage());
        }

        try {
            if (sshClient != null && sshClient.isConnected()) {
                sshClient.disconnect();
            }
        } catch (IOException e) {
            log.error("Error disconnecting SSH client: {}", e.getMessage());
        }

        if (forwarderThread != null && forwarderThread.isAlive()) {
            forwarderThread.interrupt();
            try {
                forwarderThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("SSH tunnel closed on local port {}", localPort);
    }
}

package tech.nomad4.backupmanager.isolate.socket.connection;

import com.github.dockerjava.api.DockerClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import tech.nomad4.backupmanager.isolate.socket.service.SocatSetupResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Docker connection via an SSH tunnel with socat relay.
 * <p>
 * On {@link #close()}, stops any socat process that was started by this application
 * (using the existing SSH session before the tunnel is torn down), then closes
 * the Docker client and the SSH tunnel.
 * </p>
 *
 * @see SSHTunnel
 */
@Slf4j
public class SSHDockerConnection implements DockerConnection {

    private final Long socketId;
    private final DockerClient client;
    private final SSHTunnel tunnel;

    /** Socat metadata - non-null if socat was set up for this connection. */
    @Getter
    private final SocatSetupResult socatResult;

    public SSHDockerConnection(Long socketId, DockerClient client, SSHTunnel tunnel, SocatSetupResult socatResult) {
        this.socketId = socketId;
        this.client = client;
        this.tunnel = tunnel;
        this.socatResult = socatResult;
    }

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
        if (!tunnel.isActive()) {
            log.debug("SSH tunnel is not active for socket {}", socketId);
            return false;
        }
        try {
            client.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.debug("Ping failed for SSH socket {}: {}", socketId, e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        try {
            client.close();
            log.info("Closed Docker client for socket {}", socketId);
        } catch (IOException e) {
            log.error("Error closing Docker client for socket {}", socketId, e);
        }

        // Kill socat before the SSH connection is closed
        if (socatResult != null && socatResult.isManagedByUs() && socatResult.getPid() != null) {
            killSocat(socatResult.getPid());
        }

        tunnel.close();
    }

    /**
     * Kills the socat process on the remote host using the still-open SSH connection.
     */
    private void killSocat(int pid) {
        try {
            Session session = tunnel.getSshClient().startSession();
            try {
                Command cmd = session.exec("kill " + pid);
                cmd.join(5, TimeUnit.SECONDS);
                log.info("Stopped socat process (PID: {}) for socket {}", pid, socketId);
            } finally {
                session.close();
            }
        } catch (Exception e) {
            log.warn("Failed to stop socat process (PID: {}) for socket {}: {}", pid, socketId, e.getMessage());
        }
    }
}

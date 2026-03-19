package tech.nomad4.backupmanager.isolate.socket.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for detecting the Docker runtime environment.
 * <p>
 * Determines whether the application is running inside a Docker container
 * and whether the Docker socket is accessible. Used during startup to
 * decide whether to create a system-managed local Docker socket.
 * </p>
 */
@Slf4j
public class DockerEnvironmentDetector {

    private static final String DOCKER_ENV_FILE = "/.dockerenv";
    private static final String CGROUP_FILE = "/proc/1/cgroup";
    private static final String DEFAULT_SOCKET_PATH = "/var/run/docker.sock";

    private DockerEnvironmentDetector() {
        // Utility class
    }

    /**
     * Detects whether the application is running inside a Docker container.
     * <p>
     * Uses two detection methods:
     * <ol>
     *   <li>Checks for the presence of {@code /.dockerenv}</li>
     *   <li>Checks {@code /proc/1/cgroup} for Docker or Kubernetes indicators</li>
     * </ol>
     * </p>
     *
     * @return {@code true} if running inside a Docker container
     */
    public static boolean isRunningInDocker() {
        if (new File(DOCKER_ENV_FILE).exists()) {
            return true;
        }

        try {
            Path cgroupPath = Path.of(CGROUP_FILE);
            if (Files.exists(cgroupPath)) {
                String content = Files.readString(cgroupPath);
                return content.contains("docker") || content.contains("kubepods");
            }
        } catch (Exception e) {
            log.debug("Could not read cgroup file: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Checks whether the Docker socket is available at the default path.
     *
     * @return {@code true} if the socket exists and is accessible
     */
    public static boolean isDockerSocketAvailable() {
        return isDockerSocketAvailable(DEFAULT_SOCKET_PATH);
    }

    /**
     * Checks whether a Docker socket is available at the specified path.
     *
     * @param socketPath the file system path to check
     * @return {@code true} if the socket exists and is readable and writable
     */
    public static boolean isDockerSocketAvailable(String socketPath) {
        File socket = new File(socketPath);

        if (!socket.exists()) {
            log.debug("Docker socket does not exist: {}", socketPath);
            return false;
        }

        if (!socket.canRead() || !socket.canWrite()) {
            log.debug("Docker socket exists but not accessible: {}", socketPath);
            return false;
        }

        return true;
    }

    /**
     * Returns environment-specific setup recommendations for Docker socket access.
     * <p>
     * Provides different instructions depending on whether the application is running
     * inside a Docker container or directly on the host.
     * </p>
     *
     * <p><strong>Security warning:</strong> Mounting the Docker socket gives the container
     * full access to the host's Docker daemon. Only use in trusted environments.</p>
     *
     * @return a multi-line string with setup recommendations
     */
    public static String getSetupRecommendations() {
        boolean inDocker = isRunningInDocker();

        if (inDocker) {
            return "Running in Docker but socket not available. " +
                    "Mount the Docker socket in docker-compose.yml: " +
                    "volumes: ['/var/run/docker.sock:/var/run/docker.sock:ro']. " +
                    "WARNING: This gives the container access to the host's Docker daemon.";
        } else {
            return "Docker socket not found at /var/run/docker.sock. " +
                    "Ensure Docker is installed and running (sudo systemctl status docker), " +
                    "check socket permissions (ls -la /var/run/docker.sock), " +
                    "or add your user to the docker group (sudo usermod -aG docker $USER).";
        }
    }
}

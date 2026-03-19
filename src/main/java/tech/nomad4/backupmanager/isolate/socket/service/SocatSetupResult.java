package tech.nomad4.backupmanager.isolate.socket.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the result of a socat setup operation on a remote host.
 * <p>
 * Socat is used to relay traffic between a TCP port and the Docker Unix socket
 * on the remote host. This class captures whether socat was already running
 * (external setup) or was started and is managed by this application.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public class SocatSetupResult {

    /** The TCP port that socat is listening on. */
    private final int port;

    /** Whether this application started and owns the socat process. */
    private final boolean managedByUs;

    /** The PID of the socat process, or {@code null} if externally managed. */
    private final Integer pid;

    /** Human-readable description of the socat setup state. */
    private final String description;

    /**
     * Creates a result indicating that an externally managed socat (or Docker TCP)
     * was found already running on the specified port.
     *
     * @param port the port with existing Docker TCP access
     * @return a result for an externally managed setup
     */
    public static SocatSetupResult external(int port) {
        return new SocatSetupResult(
                port,
                false,
                null,
                "Using existing Docker TCP on port " + port + " (external setup)"
        );
    }

    /**
     * Creates a result indicating that socat was started by this application.
     *
     * @param port the port that socat is listening on
     * @param pid  the PID of the socat process
     * @return a result for an application-managed socat process
     */
    public static SocatSetupResult managedByUs(int port, int pid) {
        return new SocatSetupResult(
                port,
                true,
                pid,
                "socat started and managed by application (PID: " + pid + ", port: " + port + ")"
        );
    }
}

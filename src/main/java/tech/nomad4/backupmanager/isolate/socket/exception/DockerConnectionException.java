package tech.nomad4.backupmanager.isolate.socket.exception;

/**
 * Thrown when a connection to a Docker daemon fails.
 * <p>
 * This may occur during SSH tunnel setup, socat configuration,
 * or when the Docker daemon itself is unreachable.
 * </p>
 */
public class DockerConnectionException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param message a description of the connection failure
     */
    public DockerConnectionException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message a description of the connection failure
     * @param cause   the underlying exception that caused the failure
     */
    public DockerConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

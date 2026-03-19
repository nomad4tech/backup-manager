package tech.nomad4.backupmanager.isolate.discovery.exception;

/**
 * Thrown when container discovery or inspection fails.
 * <p>
 * This may occur when the Docker client cannot list containers,
 * a specific container cannot be found, or inspection fails.
 * </p>
 */
public class ContainerDiscoveryException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param message a description of the discovery failure
     */
    public ContainerDiscoveryException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message a description of the discovery failure
     * @param cause   the underlying exception that caused the failure
     */
    public ContainerDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

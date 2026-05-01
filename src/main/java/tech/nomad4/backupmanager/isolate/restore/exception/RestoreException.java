package tech.nomad4.backupmanager.isolate.restore.exception;

/**
 * Thrown when a database restore operation fails or cannot be started.
 */
public class RestoreException extends RuntimeException {

    public RestoreException(String message) {
        super(message);
    }

    public RestoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

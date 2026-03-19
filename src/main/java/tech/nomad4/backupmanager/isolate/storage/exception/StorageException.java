package tech.nomad4.backupmanager.isolate.storage.exception;

/**
 * Thrown when a file system operation fails.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

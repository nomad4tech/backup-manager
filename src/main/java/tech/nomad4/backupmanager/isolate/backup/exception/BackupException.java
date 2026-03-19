package tech.nomad4.backupmanager.isolate.backup.exception;

/**
 * Thrown when a database backup operation fails or cannot be started.
 */
public class BackupException extends RuntimeException {

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}

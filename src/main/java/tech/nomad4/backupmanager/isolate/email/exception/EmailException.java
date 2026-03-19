package tech.nomad4.backupmanager.isolate.email.exception;

/**
 * Thrown when an email cannot be sent due to a configuration or transport error.
 */
public class EmailException extends RuntimeException {

    public EmailException(String message) {
        super(message);
    }

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}

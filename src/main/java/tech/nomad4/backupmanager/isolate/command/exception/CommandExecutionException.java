package tech.nomad4.backupmanager.isolate.command.exception;

/**
 * Thrown when a command cannot be executed or its result cannot be retrieved.
 */
public class CommandExecutionException extends RuntimeException {

    public CommandExecutionException(String message) {
        super(message);
    }

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

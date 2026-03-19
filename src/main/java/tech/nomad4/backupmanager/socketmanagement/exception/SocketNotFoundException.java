package tech.nomad4.backupmanager.socketmanagement.exception;

/**
 * Thrown when a Docker socket configuration cannot be found in the database.
 */
public class SocketNotFoundException extends RuntimeException {

    public SocketNotFoundException(Long socketId) {
        super("Docker socket not found with id: " + socketId);
    }

    public SocketNotFoundException(String name) {
        super("Docker socket not found with name: " + name);
    }
}

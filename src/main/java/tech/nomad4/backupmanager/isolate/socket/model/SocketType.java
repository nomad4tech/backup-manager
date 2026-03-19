package tech.nomad4.backupmanager.isolate.socket.model;

/**
 * Defines the connection type for a Docker socket.
 */
public enum SocketType {

    /** Direct connection to a local Docker Unix socket. */
    LOCAL,

    /** Connection to a remote Docker host via SSH tunnel with socat relay. */
    REMOTE_SSH
}

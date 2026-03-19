package tech.nomad4.backupmanager.socketmanagement.entity;

/**
 * Represents the current connection state of a Docker socket.
 */
public enum ConnectionStatus {

    /** No active connection. Initial state and state after disconnection. */
    DISCONNECTED,

    /** Connection is being established (SSH tunnel setup, socat check, etc.). */
    CONNECTING,

    /** Successfully connected to the Docker daemon. */
    CONNECTED,

    /** Connection attempt failed or an active connection was lost. */
    ERROR
}

package tech.nomad4.backupmanager.socketmanagement.entity;

/**
 * Describes the current state of the socat relay for a Docker socket connection.
 */
public enum SocatStatus {

    /** Local socket connection; socat relay is not needed. */
    NOT_NEEDED,

    /** Using an externally managed socat or Docker TCP endpoint. */
    EXTERNAL,

    /** Socat was started by this application and is managed by it. */
    MANAGED_BY_US,

    /** Socat should be running but is not (error state). */
    NOT_RUNNING
}

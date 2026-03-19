package tech.nomad4.backupmanager.backuptask.entity;

/**
 * Operational status of a backup task.
 */
public enum TaskStatus {

    /** No backup is currently running; waiting for next scheduled trigger. */
    IDLE,

    /** A backup is currently in progress. */
    RUNNING,

    /** The last backup run ended with an error. */
    ERROR,

    /** Task is disabled and will not be scheduled. */
    DISABLED
}

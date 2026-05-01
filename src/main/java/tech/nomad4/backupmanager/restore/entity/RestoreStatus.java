package tech.nomad4.backupmanager.restore.entity;

public enum RestoreStatus {

    /** Restore has been submitted and is waiting to be picked up. */
    PENDING,

    /** Restore is currently in progress. */
    RUNNING,

    /** Restore completed successfully. */
    SUCCESS,

    /** Restore failed due to an error. */
    FAILED,

    /** Restore was cancelled by the user. */
    CANCELLED
}

package tech.nomad4.backupmanager.isolate.command.dto;

/**
 * Lifecycle state of a command execution operation.
 */
public enum ExecutionState {

    /** Operation is queued but has not started yet. */
    QUEUED,

    /** Operation is currently executing. */
    RUNNING,

    /** Operation finished successfully. */
    COMPLETED,

    /** Operation finished with an error. */
    FAILED,

    /** Operation was manually cancelled. */
    CANCELLED,

    /** Operation exceeded its timeout limit. */
    TIMEOUT
}

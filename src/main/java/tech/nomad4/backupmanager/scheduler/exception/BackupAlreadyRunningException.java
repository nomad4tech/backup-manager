package tech.nomad4.backupmanager.scheduler.exception;

/**
 * Thrown when a backup execution is requested for a task that already has a
 * {@code RUNNING} record in the database.
 */
public class BackupAlreadyRunningException extends RuntimeException {

    public BackupAlreadyRunningException(Long taskId) {
        super("Backup is already running for task " + taskId);
    }
}

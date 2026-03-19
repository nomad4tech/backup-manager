package tech.nomad4.backupmanager.backuphistory.entity;

/**
 * Execution status of a single backup run.
 */
public enum BackupStatus {

    /** Backup is currently in progress. */
    RUNNING,

    /** Backup completed successfully; AWS upload not configured or disabled. */
    SUCCESS,

    /** Backup failed due to an error. */
    FAILED,

    /** Backup file created; upload to S3 is in progress. */
    UPLOADING,

    /** Backup file uploaded to S3 successfully. */
    UPLOADED,

    /** Backup file created successfully but S3 upload failed. */
    UPLOAD_FAILED,

    /** Synthetic record created at task setup time to seed the size estimator. Not a real backup run. */
    SEEDED
}

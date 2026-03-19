package tech.nomad4.backupmanager.backuptask.entity;

/**
 * Defines how a backup task is triggered on a schedule.
 */
public enum ScheduleType {

    /** Triggered according to a cron expression (e.g., every day at 02:00). */
    CRON,

    /** Triggered repeatedly with a fixed delay between runs (in seconds). */
    DELAY
}

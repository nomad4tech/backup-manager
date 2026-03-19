package tech.nomad4.backupmanager.backuptask.event;

import lombok.Getter;
import tech.nomad4.backupmanager.backuptask.entity.BackupTask;

/**
 * Published when a new {@link BackupTask} is created and should be scheduled.
 */
@Getter
public class BackupTaskCreatedEvent {
    private final BackupTask task;

    public BackupTaskCreatedEvent(BackupTask task) {
        this.task = task;
    }
}

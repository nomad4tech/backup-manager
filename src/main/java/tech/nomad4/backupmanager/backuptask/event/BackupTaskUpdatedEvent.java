package tech.nomad4.backupmanager.backuptask.event;

import lombok.Getter;
import tech.nomad4.backupmanager.backuptask.entity.BackupTask;

/**
 * Published when a {@link BackupTask} is updated and its schedule needs to be re-applied.
 */
@Getter
public class BackupTaskUpdatedEvent {
    private final BackupTask task;

    public BackupTaskUpdatedEvent(BackupTask task) {
        this.task = task;
    }
}

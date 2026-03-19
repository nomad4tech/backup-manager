package tech.nomad4.backupmanager.backuptask.event;

import lombok.Getter;

/**
 * Published when a {@link tech.nomad4.backupmanager.backuptask.entity.BackupTask}
 * is deleted and its schedule should be cancelled.
 */
@Getter
public class BackupTaskDeletedEvent {
    private final Long taskId;

    public BackupTaskDeletedEvent(Long taskId) {
        this.taskId = taskId;
    }
}

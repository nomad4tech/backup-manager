package tech.nomad4.backupmanager.backuptask.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.nomad4.backupmanager.backuptask.entity.BackupTask;

import java.util.List;

@Repository
public interface BackupTaskRepository extends JpaRepository<BackupTask, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsBySocketIdAndContainerIdAndDatabaseName(Long socketId, String containerId, String databaseName);

    /** Used by the scheduler on startup to load all tasks that should be scheduled. */
    List<BackupTask> findByEnabledTrue();
}

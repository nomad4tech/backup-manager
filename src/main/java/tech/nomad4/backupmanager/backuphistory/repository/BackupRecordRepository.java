package tech.nomad4.backupmanager.backuphistory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.nomad4.backupmanager.backuphistory.entity.BackupRecord;
import tech.nomad4.backupmanager.backuphistory.entity.BackupStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {

    Page<BackupRecord> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<BackupRecord> findByTaskIdOrderByStartedAtDesc(Long taskId, Pageable pageable);

    Page<BackupRecord> findByStatusOrderByStartedAtDesc(BackupStatus status, Pageable pageable);

    Page<BackupRecord> findByTaskIdAndStatusOrderByStartedAtDesc(Long taskId, BackupStatus status, Pageable pageable);

    Page<BackupRecord> findByStartedAtBetweenOrderByStartedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    /** Used by the scheduler to check if a task is already running. */
    boolean existsByTaskIdAndStatus(Long taskId, BackupStatus status);

    /** Used by the scheduler to check if a task is already running or uploading. */
    boolean existsByTaskIdAndStatusIn(Long taskId, Collection<BackupStatus> statuses);

    /** Used by cleanup: last successful backup for size estimation. */
    Optional<BackupRecord> findTopByTaskIdAndStatusOrderByCompletedAtDesc(Long taskId, BackupStatus status);

    /** Used by size estimation when AWS upload is enabled: considers SUCCESS, UPLOADED, UPLOAD_FAILED. */
    Optional<BackupRecord> findTopByTaskIdAndStatusInOrderByCompletedAtDesc(Long taskId, Collection<BackupStatus> statuses);

    /** Used by cleanup after file deletion: find records whose files were deleted. */
    List<BackupRecord> findByFilePathIn(List<String> filePaths);

    /** Used when a task is deleted: nullify taskId references. */
    List<BackupRecord> findByTaskId(Long taskId);

    /** Used by the restore flow: backup records that have a file on disk. */
    List<BackupRecord> findByFilePathNotNullOrderByStartedAtDesc();
}

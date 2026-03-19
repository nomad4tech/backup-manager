package tech.nomad4.backupmanager.backuphistory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.nomad4.backupmanager.backuphistory.entity.BackupRecord;
import tech.nomad4.backupmanager.backuphistory.entity.BackupStatus;
import tech.nomad4.backupmanager.backuphistory.repository.BackupRecordRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages backup execution records.
 * <p>
 * Provides querying with optional filters and deletion (record + file on disk).
 * Record creation and status updates are performed by the scheduler/orchestrator.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupRecordService {

    private final BackupRecordRepository repository;

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public Page<BackupRecord> findAll(Long taskId, BackupStatus status,
                                      LocalDateTime from, LocalDateTime to,
                                      int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (taskId != null && status != null) {
            return repository.findByTaskIdAndStatusOrderByStartedAtDesc(taskId, status, pageable);
        }
        if (taskId != null) {
            return repository.findByTaskIdOrderByStartedAtDesc(taskId, pageable);
        }
        if (status != null) {
            return repository.findByStatusOrderByStartedAtDesc(status, pageable);
        }
        if (from != null && to != null) {
            return repository.findByStartedAtBetweenOrderByStartedAtDesc(from, to, pageable);
        }
        return repository.findAllByOrderByStartedAtDesc(pageable);
    }

    public BackupRecord findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Backup record not found: " + id));
    }

    // -------------------------------------------------------------------------
    // Deletion
    // -------------------------------------------------------------------------

    /**
     * Deletes the backup record and the associated file on disk (if it exists).
     *
     * @param id backup record ID
     */
    @Transactional
    public void delete(Long id) {
        BackupRecord record = findById(id);
        deleteFileIfExists(record.getFilePath());
        repository.deleteById(id);
        log.info("Deleted backup record {} (task: {}, file: {})", id, record.getTaskName(), record.getFilePath());
    }

    /**
     * Called by the scheduler after successful cleanup: marks records as having
     * their files deleted by setting filePath to null, preserving the history entry.
     *
     * @param filePaths list of paths deleted from disk
     */
    @Transactional
    public void markFilesDeleted(List<String> filePaths) {
        List<BackupRecord> records = repository.findByFilePathIn(filePaths);
        records.forEach(r -> {
            log.debug("Marking file as deleted in record {}: {}", r.getId(), r.getFilePath());
            r.setFilePath(null);
            r.setFileSizeBytes(null);
        });
        repository.saveAll(records);
    }

    /**
     * When a task is deleted, nullifies the taskId in all its records so
     * history is preserved without a dangling reference.
     *
     * @param taskId task being deleted
     */
    @Transactional
    public void detachTask(Long taskId) {
        List<BackupRecord> records = repository.findByTaskId(taskId);
        records.forEach(r -> r.setTaskId(null));
        repository.saveAll(records);
        log.info("Detached {} backup record(s) from deleted task {}", records.size(), taskId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void deleteFileIfExists(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        try {
            Path path = Path.of(filePath);
            if (Files.deleteIfExists(path)) {
                log.info("Deleted backup file: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Could not delete backup file {}: {}", filePath, e.getMessage());
        }
    }
}

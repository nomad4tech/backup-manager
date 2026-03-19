package tech.nomad4.backupmanager.isolate.backup.service;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupCommand;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupResult;
import tech.nomad4.backupmanager.isolate.backup.exception.BackupException;
import tech.nomad4.backupmanager.isolate.backup.strategy.BackupStrategy;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.storage.service.StorageService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Executes database backups by delegating to the appropriate {@link BackupStrategy}.
 * <p>
 * This service is intentionally narrow in scope: it selects the right strategy
 * for the requested {@link DatabaseType}, times the execution, and returns a
 * {@link BackupResult} with the outcome.
 * </p>
 * <p>
 * It does <strong>not</strong> create directories, verify output files, or
 * calculate checksums - those are storage-level concerns handled by the caller
 * (orchestrator or dev runner) using
 * {@link StorageService}.
 * </p>
 *
 * <h3>Adding a new database engine</h3>
 * Implement {@link BackupStrategy} and annotate the class with {@code @Component}.
 * Spring will register it automatically; no changes to this service are needed.
 */
@Slf4j
public class BackupExecutionService {

    private final List<BackupStrategy> strategies;

    public BackupExecutionService(List<BackupStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Executes a backup for the database type specified in {@code command}.
     * <p>
     * The output directory must exist before calling this method. This method
     * always returns a result; it never throws. Check {@link BackupResult#isSuccess()}
     * to determine the outcome.
     * </p>
     *
     * @param dockerClient authenticated Docker client for the target host
     * @param command       backup parameters: container, database, output path,
     *                      database type, and options
     * @return execution result - success/failure and timing; file metadata is
     *         not included and must be retrieved separately
     */
    public BackupResult executeBackup(DockerClient dockerClient, BackupCommand command) {
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Starting backup: container={}, database={}, type={}, output={}",
                command.getContainerId(),
                command.getDatabaseName(),
                command.getDatabaseType(),
                command.getOutputFilePath());

        try {
            BackupStrategy strategy = resolveStrategy(command.getDatabaseType());

            strategy.execute(dockerClient, command);

            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            log.info("Backup completed in {} ms: {}", durationMs, command.getOutputFilePath());

            return BackupResult.builder()
                    .filePath(command.getOutputFilePath())
                    .durationMs(durationMs)
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Backup failed for database '{}' in container {}: {}",
                    command.getDatabaseName(), command.getContainerId(), e.getMessage(), e);

            return BackupResult.builder()
                    .filePath(command.getOutputFilePath())
                    .durationMs(Duration.between(startTime, LocalDateTime.now()).toMillis())
                    .startedAt(startTime)
                    .completedAt(LocalDateTime.now())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private BackupStrategy resolveStrategy(DatabaseType type) {
        return strategies.stream()
                .filter(s -> s.getSupportedType() == type)
                .findFirst()
                .orElseThrow(() -> new BackupException(
                        "No backup strategy registered for database type: " + type));
    }
}

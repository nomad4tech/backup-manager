package tech.nomad4.backupmanager.isolate.restore.service;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.restore.dto.RestoreCommand;
import tech.nomad4.backupmanager.isolate.restore.dto.RestoreResult;
import tech.nomad4.backupmanager.isolate.restore.exception.RestoreException;
import tech.nomad4.backupmanager.isolate.restore.strategy.RestoreStrategy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Executes database restores by delegating to the appropriate {@link RestoreStrategy}.
 * <p>
 * This service is intentionally narrow in scope: it selects the right strategy
 * for the requested {@link DatabaseType}, times the execution, and returns a
 * {@link RestoreResult} with the outcome.
 * </p>
 * <p>
 * This method always returns a result; it never throws. Check
 * {@link RestoreResult#isSuccess()} to determine the outcome.
 * </p>
 *
 * <h3>Adding a new database engine</h3>
 * Implement {@link RestoreStrategy} and annotate the class with {@code @Component}.
 * Spring will register it automatically; no changes to this service are needed.
 */
@Slf4j
public class RestoreExecutionService {

    private final List<RestoreStrategy> strategies;

    public RestoreExecutionService(List<RestoreStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Executes a restore for the database type specified in {@code command}.
     * <p>
     * Always returns a result; never throws. Check {@link RestoreResult#isSuccess()}
     * to determine the outcome.
     * </p>
     *
     * @param dockerClient authenticated Docker client for the target host
     * @param command       restore parameters: container, database, input path, and database type
     * @return execution result - success/failure and timing
     */
    public RestoreResult executeRestore(DockerClient dockerClient, RestoreCommand command) {
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Starting restore: container={}, database={}, type={}, input={}",
                command.getContainerId(),
                command.getDatabaseName(),
                command.getDatabaseType(),
                command.getInputFilePath());

        try {
            RestoreStrategy strategy = resolveStrategy(command.getDatabaseType());

            strategy.execute(dockerClient, command);

            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            log.info("Restore completed in {} ms: {}", durationMs, command.getInputFilePath());

            return RestoreResult.builder()
                    .inputFilePath(command.getInputFilePath())
                    .durationMs(durationMs)
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Restore failed for database '{}' in container {}: {}",
                    command.getDatabaseName(), command.getContainerId(), e.getMessage(), e);

            return RestoreResult.builder()
                    .inputFilePath(command.getInputFilePath())
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

    private RestoreStrategy resolveStrategy(DatabaseType type) {
        return strategies.stream()
                .filter(s -> s.getSupportedType() == type)
                .findFirst()
                .orElseThrow(() -> new RestoreException(
                        "No restore strategy registered for database type: " + type));
    }
}

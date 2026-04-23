package tech.nomad4.backupmanager.backuptask.service;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.nomad4.backupmanager.backuphistory.entity.BackupRecord;
import tech.nomad4.backupmanager.backuphistory.entity.BackupStatus;
import tech.nomad4.backupmanager.backuphistory.repository.BackupRecordRepository;
import tech.nomad4.backupmanager.backuphistory.service.BackupRecordService;
import tech.nomad4.backupmanager.backuptask.dto.BackupTaskRequest;
import tech.nomad4.backupmanager.backuptask.dto.BackupTaskResponse;
import tech.nomad4.backupmanager.backuptask.entity.BackupTask;
import tech.nomad4.backupmanager.backuptask.entity.ScheduleType;
import tech.nomad4.backupmanager.backuptask.entity.TaskStatus;
import tech.nomad4.backupmanager.backuptask.event.BackupTaskCreatedEvent;
import tech.nomad4.backupmanager.backuptask.event.BackupTaskDeletedEvent;
import tech.nomad4.backupmanager.backuptask.event.BackupTaskUpdatedEvent;
import tech.nomad4.backupmanager.backuptask.repository.BackupTaskRepository;
import tech.nomad4.backupmanager.isolate.discovery.entity.ContainerInfo;
import tech.nomad4.backupmanager.isolate.discovery.entity.ContainerState;
import tech.nomad4.backupmanager.isolate.discovery.service.ContainerDiscoveryService;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.command.service.DatabaseCommandService;
import tech.nomad4.backupmanager.isolate.command.service.MysqlCommandService;
import tech.nomad4.backupmanager.isolate.command.service.PostgresCommandService;
import tech.nomad4.backupmanager.socketmanagement.entity.DockerSocket;
import tech.nomad4.backupmanager.socketmanagement.exception.SocketNotFoundException;
import tech.nomad4.backupmanager.socketmanagement.repository.DockerSocketRepository;
import tech.nomad4.backupmanager.socketmanagement.service.DockerSocketFacadeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages backup task lifecycle: CRUD with cross-package validation and
 * publishing of Spring events so the scheduler can react without coupling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupTaskService {

    private final BackupTaskRepository taskRepository;
    private final DockerSocketRepository socketRepository;
    private final DockerSocketFacadeService socketFacade;
    private final ContainerDiscoveryService discoveryService;
    private final BackupRecordService backupRecordService;
    private final BackupRecordRepository recordRepository;
    private final PostgresCommandService postgresCommandService;
    private final MysqlCommandService mysqlCommandService;
    private final ApplicationEventPublisher eventPublisher;

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<BackupTaskResponse> findAll() {
        return taskRepository.findAll().stream()
                .map(task -> BackupTaskResponse.from(task, resolveSocketName(task.getSocketId())))
                .collect(Collectors.toList());
    }

    public BackupTaskResponse findById(Long id) {
        BackupTask task = getTask(id);
        return BackupTaskResponse.from(task, resolveSocketName(task.getSocketId()));
    }

    private BackupTask getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Backup task not found: " + id));
    }

    private String resolveSocketName(Long socketId) {
        return socketRepository.findById(socketId)
                .map(DockerSocket::getName)
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Transactional
    public BackupTask create(BackupTaskRequest request) {
        validateName(request.getName(), null);
        validateNoDuplicateDatabase(request.getSocketId(), request.getContainerId(), request.getDatabaseName());
        normalizeAndValidateSchedule(request);
        validateSocketExists(request.getSocketId());
        String containerName = resolveContainerName(request.getSocketId(), request.getContainerId());

        BackupTask task = new BackupTask();
        applyRequest(request, task);
        task.setContainerName(containerName);

        BackupTask saved = taskRepository.save(task);
        log.info("Created backup task: {} (id={})", saved.getName(), saved.getId());

        if (Boolean.TRUE.equals(saved.getEnabled())) {
            eventPublisher.publishEvent(new BackupTaskCreatedEvent(saved));
        }

        seedDatabaseSize(saved);

        return saved;
    }

    @Transactional
    public BackupTask update(Long id, BackupTaskRequest request) {
        BackupTask task = getTask(id);

        validateName(request.getName(), id);
        normalizeAndValidateSchedule(request);
        validateSocketExists(request.getSocketId());
        String containerName = resolveContainerName(request.getSocketId(), request.getContainerId());

        applyRequest(request, task);
        task.setContainerName(containerName);

        BackupTask saved = taskRepository.save(task);
        log.info("Updated backup task: {} (id={})", saved.getName(), saved.getId());

        eventPublisher.publishEvent(new BackupTaskUpdatedEvent(saved));
        return saved;
    }

    /**
     * Toggles enabled state without affecting schedule config or status.
     */
    @Transactional
    public BackupTask toggle(Long id) {
        BackupTask task = getTask(id);
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        task.setStatus(Boolean.TRUE.equals(task.getEnabled()) ? TaskStatus.IDLE : TaskStatus.DISABLED);
        task.setNextScheduledAt(null);
        BackupTask saved = taskRepository.save(task);
        log.info("Toggled task {} → enabled={}", id, saved.getEnabled());
        eventPublisher.publishEvent(new BackupTaskUpdatedEvent(saved));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        BackupTask task = getTask(id);
        // Preserve history records - nullify the taskId reference
        backupRecordService.detachTask(id);
        taskRepository.deleteById(id);
        log.info("Deleted backup task: {} (id={})", task.getName(), id);
        eventPublisher.publishEvent(new BackupTaskDeletedEvent(id));
    }

    // -------------------------------------------------------------------------
    // Database size seeding
    // -------------------------------------------------------------------------

    /**
     * Queries the actual database size from the container and saves a synthetic
     * {@link BackupStatus#SEEDED} record so that {@code estimateBackupSize()} has
     * a real baseline on the very first scheduled run.
     * <p>
     * All failures are swallowed - this must never prevent task creation from
     * succeeding.
     * </p>
     */
    private void seedDatabaseSize(BackupTask task) {
        try {
            DatabaseCommandService commandService = resolveCommandService(task.getDatabaseType());
            DockerClient client = socketFacade.getDockerClient(task.getSocketId());
            long size = commandService.getDatabaseSize(client, task.getContainerId(), task.getDatabaseName());

            LocalDateTime now = LocalDateTime.now();
            BackupRecord seed = new BackupRecord();
            seed.setTaskId(task.getId());
            seed.setTaskName(task.getName());
            seed.setSocketId(task.getSocketId());
            seed.setContainerId(task.getContainerId());
            seed.setContainerName(task.getContainerName());
            seed.setDatabaseName(task.getDatabaseName());
            seed.setStatus(BackupStatus.SEEDED);
            seed.setStartedAt(now);
            seed.setCompletedAt(now);
            seed.setDurationMs(0L);
            seed.setFilePath("");
            seed.setFileSizeBytes(size);
            recordRepository.save(seed);

            log.info("Seeded database size for task {} ({}): {} bytes", task.getId(), task.getName(), size);
        } catch (Exception e) {
            log.warn("Could not seed database size for task {} ({}), skipping: {}",
                    task.getId(), task.getName(), e.getMessage());
        }
    }

    private DatabaseCommandService resolveCommandService(DatabaseType databaseType) {
        return switch (databaseType) {
            case POSTGRES        -> postgresCommandService;
            case MYSQL, MARIADB  -> mysqlCommandService;
            default -> throw new IllegalArgumentException(
                    "Database size query not supported for type: " + databaseType);
        };
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateNoDuplicateDatabase(Long socketId, String containerId, String databaseName) {
        if (taskRepository.existsBySocketIdAndContainerIdAndDatabaseName(socketId, containerId, databaseName)) {
            throw new IllegalArgumentException("A backup task for this database already exists");
        }
    }

    private void validateName(String name, Long excludeId) {
        boolean exists = excludeId == null
                ? taskRepository.existsByName(name)
                : taskRepository.existsByNameAndIdNot(name, excludeId);
        if (exists) {
            throw new IllegalArgumentException("Task name '" + name + "' is already taken");
        }
    }

    private void normalizeAndValidateSchedule(BackupTaskRequest request) {
        request.setCronExpression(normalizeCron(request.getCronExpression()));

        if (request.getScheduleType() == ScheduleType.CRON) {
            if (request.getCronExpression() == null || request.getCronExpression().isBlank()) {
                throw new IllegalArgumentException("cronExpression is required when scheduleType=CRON");
            }
            if (!CronExpression.isValidExpression(request.getCronExpression())) {
                throw new IllegalArgumentException(
                        "Invalid cron expression: '" + request.getCronExpression() + "'");
            }
        } else if (request.getScheduleType() == ScheduleType.DELAY) {
            if (request.getDelayHours() == null) {
                throw new IllegalArgumentException("delayHours is required when scheduleType=DELAY");
            }
            if (request.getDelayHours() < 1 || request.getDelayHours() > 168) {
                throw new IllegalArgumentException("delayHours must be between 1 and 168");
            }
        }
    }

    private void validateSocketExists(Long socketId) {
        if (!socketRepository.existsById(socketId)) {
            throw new SocketNotFoundException(socketId);
        }
    }

    /**
     * Validates that the container exists on the given socket and returns its name.
     * The name is stored alongside the ID so that if the container is recreated
     * (new ID after docker-compose down/up), the scheduler can find it by name.
     */
    private String resolveContainerName(Long socketId, String containerId) {
        try {
            var client = socketFacade.getDockerClient(socketId);
            ContainerInfo info = discoveryService.getContainerInfo(client, containerId);
            if (info.getState() != ContainerState.RUNNING) {
                log.warn("Container {} ({}) exists but is not running", containerId, info.getContainerName());
            }
            return info.getContainerName();
        } catch (SocketNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot verify container '" + containerId + "': " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private void applyRequest(BackupTaskRequest request, BackupTask task) {
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setSocketId(request.getSocketId());
        task.setContainerId(request.getContainerId());
        task.setDatabaseName(request.getDatabaseName());
        task.setDatabaseType(request.getDatabaseType());
        task.setScheduleType(request.getScheduleType());
        task.setCronExpression(request.getCronExpression());
        task.setDelaySeconds(request.getScheduleType() == ScheduleType.DELAY
                ? request.getDelayHours() * 3600L
                : null);
        task.setKeepBackupsCount(request.getKeepBackupsCount());
        task.setCompressionEnabled(request.getCompressionEnabled() != null ? request.getCompressionEnabled() : true);
        task.setUploadToS3(request.getUploadToS3() != null ? request.getUploadToS3() : true);
        task.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        if (!Boolean.TRUE.equals(task.getEnabled())) {
            task.setStatus(TaskStatus.DISABLED);
        }
    }

    // we use only hourly scheduling for now, so we allow users to omit the seconds field and prepend "0 " if needed
    private String normalizeCron(String cron) {
        if (cron == null) return null;
        return cron.trim().split("\\s+").length == 5 ? "0 " + cron.trim() : cron.trim();
    }
}

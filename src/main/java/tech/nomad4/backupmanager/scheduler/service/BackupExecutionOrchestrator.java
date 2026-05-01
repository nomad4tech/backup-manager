package tech.nomad4.backupmanager.scheduler.service;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;
import tech.nomad4.backupmanager.appsettings.service.AppSettingsService;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.UploadCommand;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.UploadResult;
import tech.nomad4.backupmanager.isolate.awsbucket.service.AwsBucketService;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupCommand;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupOptions;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupResult;
import tech.nomad4.backupmanager.isolate.backup.dto.CompressionType;
import tech.nomad4.backupmanager.isolate.backup.service.BackupExecutionService;
import tech.nomad4.backupmanager.backuphistory.entity.BackupRecord;
import tech.nomad4.backupmanager.backuphistory.entity.BackupStatus;
import tech.nomad4.backupmanager.backuphistory.repository.BackupRecordRepository;
import tech.nomad4.backupmanager.backuphistory.service.BackupRecordService;
import tech.nomad4.backupmanager.backuptask.entity.BackupTask;
import tech.nomad4.backupmanager.backuptask.entity.TaskStatus;
import tech.nomad4.backupmanager.backuptask.repository.BackupTaskRepository;
import tech.nomad4.backupmanager.isolate.email.dto.EmailClientConfig;
import tech.nomad4.backupmanager.isolate.email.dto.EmailMessage;
import tech.nomad4.backupmanager.isolate.email.service.EmailService;
import tech.nomad4.backupmanager.scheduler.config.BackupProperties;
import tech.nomad4.backupmanager.scheduler.exception.BackupAlreadyRunningException;
import tech.nomad4.backupmanager.socketmanagement.service.DockerSocketFacadeService;
import tech.nomad4.backupmanager.isolate.storage.dto.CleanupPolicy;
import tech.nomad4.backupmanager.isolate.storage.service.StorageService;
import tech.nomad4.backupmanager.restore.entity.RestoreRecord;
import tech.nomad4.backupmanager.restore.entity.RestoreStatus;
import tech.nomad4.backupmanager.restore.repository.RestoreRecordRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates the full lifecycle of a single backup run.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Guard against duplicate concurrent runs for the same task.</li>
 *   <li>Verify that sufficient disk space is available before starting.</li>
 *   <li>Create and persist a {@code RUNNING} {@link BackupRecord}.</li>
 *   <li>Delegate the actual dump to {@link BackupExecutionService}.</li>
 *   <li>Finalize the record with {@code SUCCESS} or {@code FAILED} status.</li>
 *   <li>Clean up old backup files when {@code keepBackupsCount} is set.</li>
 *   <li>Upload the backup file to S3 if AWS upload is enabled in {@link AppSettings}.</li>
 * </ol>
 * </p>
 * <p>
 * This class does <em>not</em> manage scheduling or concurrency semaphores -
 * those are the responsibility of {@link BackupSchedulerService}.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupExecutionOrchestrator {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BackupTaskRepository taskRepository;
    private final BackupRecordRepository recordRepository;
    private final BackupRecordService recordService;
    private final BackupExecutionService backupExecutionService;
    private final StorageService storageService;
    private final DockerSocketFacadeService socketFacade;
    private final ContainerResolverService containerResolver;
    private final BackupProperties props;
    private final AppSettingsService appSettingsService;
    private final AwsBucketService awsBucketService;
    private final EmailService emailService;
    private final RestoreRecordRepository restoreRecordRepository;

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Executes the full backup flow for the given task.
     *
     * @param taskId ID of the {@link BackupTask} to back up
     * @throws BackupAlreadyRunningException if a {@code RUNNING} or {@code UPLOADING} record
     *                                       already exists for this task
     */
    public void executeBackup(Long taskId) {
        BackupTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Task {} not found, skipping backup run", taskId);
            return;
        }

        // Guard: prevent duplicate concurrent runs for the same task (covers both backup and upload phases)
        if (recordRepository.existsByTaskIdAndStatusIn(taskId, List.of(BackupStatus.RUNNING, BackupStatus.UPLOADING))) {
            log.warn("Task {} ({}) already has an active record, skipping", taskId, task.getName());
            throw new BackupAlreadyRunningException(taskId);
        }

        LocalDateTime startedAt = LocalDateTime.now();
        String taskDir = props.getBaseDirectory() + "/" + task.getName();
        String extension = task.isCompressionEnabled() ? ".sql.gz" : ".sql";
        String filePath = taskDir + "/" + task.getName() + "_" + startedAt.format(TIMESTAMP_FMT) + extension;

        // Pre-flight: ensure base directory and verify disk space
        storageService.ensureDirectory(props.getBaseDirectory());
        long estimatedSize = estimateBackupSize(taskId);
        long requiredSpace = estimatedSize + props.getMinFreeSpaceBytes();
        if (!storageService.hasEnoughSpace(props.getBaseDirectory(), requiredSpace)) {
            String msg = String.format(
                    "Insufficient disk space: need ~%d bytes (estimate %d + buffer %d)",
                    requiredSpace, estimatedSize, props.getMinFreeSpaceBytes());
            log.error("Pre-flight disk check failed for task {} ({}): {}", taskId, task.getName(), msg);
            saveImmediatelyFailedRecord(task, filePath, startedAt, msg);
            task.setStatus(TaskStatus.ERROR);
            task.setLastError(msg);
            taskRepository.save(task);
            return;
        }

        // Create RUNNING record and mark task as running
        BackupRecord record = initRecord(task, filePath, startedAt);
        record.setStatus(BackupStatus.RUNNING);
        record = recordRepository.save(record);

        task.setStatus(TaskStatus.RUNNING);
        task.setLastError(null);
        taskRepository.save(task);

        // Execute backup
        try {
            storageService.ensureDirectory(taskDir);
            DockerClient client = socketFacade.getDockerClient(task.getSocketId());

            // Resolve container ID - handles recreated containers (docker-compose down/up)
            ContainerResolverService.ContainerResolution resolution =
                    containerResolver.resolve(client, task.getContainerId(), task.getContainerName());

            if (resolution.idChanged()) {
                log.warn("Auto-updating containerId for task {} ({}): '{}' → '{}'",
                        task.getId(), task.getName(), resolution.originalId(), resolution.resolvedId());
                task.setContainerId(resolution.resolvedId());
                taskRepository.save(task);
                record.setContainerId(resolution.resolvedId());
                recordRepository.save(record);
            }

            BackupOptions options = BackupOptions.defaults();
            options.setTimeoutSeconds(props.getDefaultTimeout());
            if (task.isCompressionEnabled()) {
                options.setCompression(CompressionType.GZIP);
            }

            BackupCommand command = BackupCommand.builder()
                    .containerId(resolution.resolvedId())
                    .databaseName(task.getDatabaseName())
                    .outputFilePath(filePath)
                    .databaseType(task.getDatabaseType())
                    .options(options)
                    .postgresUser("postgres")
                    .build();
            BackupResult result = backupExecutionService.executeBackup(client, command);

            if (result.isSuccess()) {
                onSuccess(task, record, result, taskDir);
            } else {
                onFailure(task, record, result.getCompletedAt(), result.getDurationMs(), result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Unexpected error during backup for task {} ({}): {}",
                    taskId, task.getName(), e.getMessage(), e);
            onFailure(task, record, LocalDateTime.now(),
                    Duration.between(startedAt, LocalDateTime.now()).toMillis(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Success / failure handlers
    // -------------------------------------------------------------------------

    private void onSuccess(BackupTask task, BackupRecord record, BackupResult result, String taskDir) {
        long fileSize = 0;
        if (storageService.fileExists(result.getFilePath())) {
            fileSize = storageService.getFileInfo(result.getFilePath()).getSizeBytes();
        }

        record.setStatus(BackupStatus.SUCCESS);
        record.setCompletedAt(result.getCompletedAt());
        record.setDurationMs(result.getDurationMs());
        record.setFileSizeBytes(fileSize);
        recordRepository.save(record);

        task.setStatus(TaskStatus.IDLE);
        task.setLastError(null);
        taskRepository.save(task);

        log.info("Backup succeeded for task {} ({}): {} bytes in {} ms",
                task.getId(), task.getName(), fileSize, result.getDurationMs());

        // Cleanup old backups if a retention limit is configured
        if (task.getKeepBackupsCount() != null) {
            Set<String> protectedPaths = restoreRecordRepository
                    .findByStatusIn(List.of(RestoreStatus.RUNNING))
                    .stream()
                    .map(RestoreRecord::getBackupFilePath)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<String> deleted = storageService.cleanupFiles(
                    taskDir, CleanupPolicy.keepLastN(task.getKeepBackupsCount()), protectedPaths);
            if (!deleted.isEmpty()) {
                recordService.markFilesDeleted(deleted);
                log.info("Cleanup: removed {} old backup(s) for task {} ({})",
                        deleted.size(), task.getId(), task.getName());
            }
        }

        // AWS upload phase
        AppSettings settings = appSettingsService.get();
        UploadResult uploadResult = null;
        if (task.isUploadToS3() && settings.isAwsEnabled() && Boolean.TRUE.equals(settings.getAwsConnectionValid())) {
            uploadResult = uploadToAws(task, record, settings);
        }

        sendNotificationIfEnabled(task, true, fileSize, result.getDurationMs(), null, uploadResult);
    }

    private void onFailure(BackupTask task, BackupRecord record,
                           LocalDateTime completedAt, long durationMs, String errorMessage) {
        record.setStatus(BackupStatus.FAILED);
        record.setCompletedAt(completedAt);
        record.setDurationMs(durationMs);
        record.setErrorMessage(errorMessage);
        recordRepository.save(record);

        task.setStatus(TaskStatus.ERROR);
        task.setLastError(errorMessage);
        taskRepository.save(task);

        log.error("Backup failed for task {} ({}): {}", task.getId(), task.getName(), errorMessage);

        sendNotificationIfEnabled(task, false, 0, durationMs, errorMessage, null);
    }

    // -------------------------------------------------------------------------
    // AWS upload
    // -------------------------------------------------------------------------

    /**
     * Uploads the backup file to S3. Updates the record status to {@code UPLOADING},
     * then to {@code UPLOADED} on success or {@code UPLOAD_FAILED} on error.
     *
     * @return the upload result, or {@code null} if bucket config could not be built
     */
    private UploadResult uploadToAws(BackupTask task, BackupRecord record, AppSettings settings) {
        var bucketConfig = appSettingsService.buildBucketConfig();
        if (bucketConfig == null) {
            log.warn("AWS upload skipped for task {} ({}) - bucket config unavailable", task.getId(), task.getName());
            return null;
        }

        String base = settings.getAwsDestinationDirectory();
        String destinationDir = (base != null && !base.isBlank())
                ? base.replaceAll("/+$", "") + "/" + task.getName()
                : task.getName();

        record.setStatus(BackupStatus.UPLOADING);
        recordRepository.save(record);

        log.info("Starting AWS upload for task {} ({}): {} → s3://{}/{}",
                task.getId(), task.getName(), record.getFilePath(),
                bucketConfig.getBucketName(), destinationDir);

        UploadResult result = awsBucketService.upload(UploadCommand.builder()
                .config(bucketConfig)
                .localFilePath(record.getFilePath())
                .destinationDirectory(destinationDir)
                .build());

        if (result.isSuccess()) {
            record.setStatus(BackupStatus.UPLOADED);
            record.setAwsKey(result.getRemoteKey());
            record.setAwsBucketName(bucketConfig.getBucketName());
            recordRepository.save(record);
            log.info("AWS upload succeeded for task {} ({}): key={}, {} ms",
                    task.getId(), task.getName(), result.getRemoteKey(), result.getDurationMs());
        } else {
            record.setStatus(BackupStatus.UPLOAD_FAILED);
            record.setErrorMessage("Upload failed: " + result.getErrorMessage());
            recordRepository.save(record);
            log.error("AWS upload failed for task {} ({}): {}",
                    task.getId(), task.getName(), result.getErrorMessage());
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a backup result notification email if the relevant flag is enabled
     * in {@link AppSettings} and email is working.
     *
     * @param task         the backup task that was run
     * @param success      {@code true} for success, {@code false} for failure
     * @param fileSize     file size in bytes (used in success message)
     * @param durationMs   duration of the backup run
     * @param errorMessage error description, or {@code null} on success
     * @param uploadResult AWS upload result, or {@code null} if upload was not attempted
     */
    private void sendNotificationIfEnabled(BackupTask task, boolean success,
                                           long fileSize, long durationMs, String errorMessage,
                                           UploadResult uploadResult) {
        AppSettings settings = appSettingsService.get();

        if (success && !settings.isNotifyOnSuccess()) return;
        if (!success && !settings.isNotifyOnFailure()) return;

        if (!Boolean.TRUE.equals(settings.getEmailConnectionValid())) {
            log.debug("Backup notification skipped for task {} - email is not connected", task.getId());
            return;
        }

        String recipients = settings.getNotificationRecipients();
        if (recipients == null || recipients.isBlank()) {
            log.debug("Backup notification skipped for task {} - no recipients configured", task.getId());
            return;
        }

        EmailClientConfig config = appSettingsService.buildEmailConfig();
        if (config == null) {
            log.debug("Backup notification skipped for task {} - email config unavailable", task.getId());
            return;
        }

        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder();

        for (String recipient : recipients.split(",")) {
            String trimmed = recipient.trim();
            if (!trimmed.isBlank()) {
                builder.to(trimmed);
            }
        }

        if (success) {
            builder.subject("Backup Manager \u2014 Backup succeeded: " + task.getName());
            StringBuilder body = new StringBuilder(String.format(
                    "Backup completed successfully.\n\nTask: %s\nDatabase: %s\nDuration: %d ms\nFile size: %d bytes",
                    task.getName(), task.getDatabaseName(), durationMs, fileSize));
            if (uploadResult != null) {
                if (uploadResult.isSuccess()) {
                    body.append(String.format("\n\nAWS Upload: OK\nS3 Key: %s", uploadResult.getRemoteKey()));
                } else {
                    body.append(String.format("\n\nAWS Upload: Failed\nError: %s", uploadResult.getErrorMessage()));
                }
            }
            builder.body(body.toString());
        } else {
            builder.subject("Backup Manager \u2014 Backup failed: " + task.getName());
            builder.body(String.format(
                    "Backup failed.\n\nTask: %s\nDatabase: %s\nDuration: %d ms\nError: %s",
                    task.getName(), task.getDatabaseName(), durationMs,
                    errorMessage != null ? errorMessage : "unknown error"));
        }

        emailService.send(config, builder.build());
    }

    /**
     * Estimates the expected backup file size based on the last successful run.
     * Considers SUCCESS, UPLOADED and UPLOAD_FAILED as valid terminal success states
     * (all three mean the file was written to disk successfully).
     * Returns {@code 0} if no previous successful backup exists.
     */
    private long estimateBackupSize(Long taskId) {
        return recordRepository
                .findTopByTaskIdAndStatusInOrderByCompletedAtDesc(
                        taskId, List.of(BackupStatus.SUCCESS, BackupStatus.UPLOADED, BackupStatus.UPLOAD_FAILED, BackupStatus.SEEDED))
                .map(r -> r.getFileSizeBytes() != null ? (long) (r.getFileSizeBytes() * 1.5) : 0L)
                .orElse(0L);
    }

    private BackupRecord initRecord(BackupTask task, String filePath, LocalDateTime startedAt) {
        BackupRecord r = new BackupRecord();
        r.setTaskId(task.getId());
        r.setTaskName(task.getName());
        r.setSocketId(task.getSocketId());
        r.setContainerId(task.getContainerId());
        r.setContainerName(task.getContainerName());
        r.setDatabaseName(task.getDatabaseName());
        r.setDatabaseType(task.getDatabaseType());
        r.setStartedAt(startedAt);
        r.setFilePath(filePath);
        return r;
    }

    /** Creates and saves a FAILED record for cases that fail before execution starts. */
    private void saveImmediatelyFailedRecord(BackupTask task, String filePath,
                                             LocalDateTime startedAt, String errorMessage) {
        BackupRecord r = initRecord(task, filePath, startedAt);
        r.setStatus(BackupStatus.FAILED);
        r.setCompletedAt(LocalDateTime.now());
        r.setDurationMs(0L);
        r.setErrorMessage(errorMessage);
        recordRepository.save(r);
    }
}

package tech.nomad4.backupmanager.restore.service;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;
import tech.nomad4.backupmanager.appsettings.service.AppSettingsService;
import tech.nomad4.backupmanager.isolate.email.dto.EmailClientConfig;
import tech.nomad4.backupmanager.isolate.email.dto.EmailMessage;
import tech.nomad4.backupmanager.isolate.email.service.EmailService;
import tech.nomad4.backupmanager.isolate.restore.dto.RestoreCommand;
import tech.nomad4.backupmanager.isolate.restore.dto.RestoreResult;
import tech.nomad4.backupmanager.isolate.restore.service.RestoreExecutionService;
import tech.nomad4.backupmanager.restore.entity.RestoreRecord;
import tech.nomad4.backupmanager.restore.entity.RestoreStatus;
import tech.nomad4.backupmanager.restore.repository.RestoreRecordRepository;
import tech.nomad4.backupmanager.socketmanagement.service.DockerSocketFacadeService;

import java.time.LocalDateTime;

/**
 * Orchestrates the full lifecycle of a single restore run.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Guard against records that are not in {@code PENDING} state.</li>
 *   <li>Transition the record through {@code RUNNING} → {@code SUCCESS} / {@code FAILED}.</li>
 *   <li>Delegate the actual restore to {@link RestoreExecutionService}.</li>
 *   <li>Send an email notification if configured.</li>
 * </ol>
 * </p>
 * <p>
 * This class does <em>not</em> manage scheduling or file-system cleanup — those
 * are not applicable to the restore flow.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestoreExecutionOrchestrator {

    private final RestoreRecordRepository restoreRecordRepository;
    private final RestoreExecutionService restoreExecutionService;
    private final DockerSocketFacadeService socketFacade;
    private final AppSettingsService appSettingsService;
    private final EmailService emailService;

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Executes the full restore flow for the given record.
     *
     * @param restoreRecordId ID of the {@link RestoreRecord} to process
     */
    public void executeRestore(Long restoreRecordId) {
        RestoreRecord record = restoreRecordRepository.findById(restoreRecordId).orElse(null);
        if (record == null) {
            log.warn("RestoreRecord {} not found, skipping restore run", restoreRecordId);
            return;
        }

        if (record.getStatus() != RestoreStatus.PENDING) {
            log.warn("RestoreRecord {} has status {} — expected PENDING, skipping",
                    restoreRecordId, record.getStatus());
            return;
        }

        record.setStatus(RestoreStatus.RUNNING);
        restoreRecordRepository.save(record);

        DockerClient client = socketFacade.getDockerClient(record.getSocketId());

        RestoreCommand command = RestoreCommand.builder()
                .containerId(record.getContainerId())
                .databaseName(record.getTargetDatabaseName())
                .inputFilePath(record.getBackupFilePath())
                .databaseType(record.getDatabaseType())
                .compressed(record.getBackupFilePath() != null && record.getBackupFilePath().endsWith(".gz"))
                .timeoutSeconds(3600)
                .build();

        RestoreResult result = restoreExecutionService.executeRestore(client, command);

        if (result.isSuccess()) {
            record.setStatus(RestoreStatus.SUCCESS);
            record.setCompletedAt(result.getCompletedAt());
            record.setDurationMs(result.getDurationMs());
            restoreRecordRepository.save(record);
            log.info("Restore succeeded for record {} (target: {}) in {} ms",
                    restoreRecordId, record.getTargetDatabaseName(), result.getDurationMs());
        } else {
            record.setStatus(RestoreStatus.FAILED);
            record.setCompletedAt(result.getCompletedAt());
            record.setDurationMs(result.getDurationMs());
            record.setErrorMessage(result.getErrorMessage());
            restoreRecordRepository.save(record);
            log.error("Restore failed for record {} (target: {}): {}",
                    restoreRecordId, record.getTargetDatabaseName(), result.getErrorMessage());
        }

        sendNotificationIfEnabled(record, result.isSuccess(), result.getErrorMessage());
    }

    /**
     * Cancels a running restore by transitioning it to {@code CANCELLED}.
     * Has no effect if the record is not in {@code RUNNING} state.
     *
     * @param restoreRecordId ID of the {@link RestoreRecord} to cancel
     */
    public void cancelRestore(Long restoreRecordId) {
        RestoreRecord record = restoreRecordRepository.findById(restoreRecordId).orElse(null);
        if (record == null) {
            log.warn("RestoreRecord {} not found, cannot cancel", restoreRecordId);
            return;
        }
        if (record.getStatus() == RestoreStatus.RUNNING) {
            record.setStatus(RestoreStatus.CANCELLED);
            record.setCompletedAt(LocalDateTime.now());
            restoreRecordRepository.save(record);
            log.info("RestoreRecord {} cancelled", restoreRecordId);
        } else {
            log.warn("RestoreRecord {} has status {} — only RUNNING records can be cancelled",
                    restoreRecordId, record.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private void sendNotificationIfEnabled(RestoreRecord record, boolean success, String errorMessage) {
        AppSettings settings = appSettingsService.get();

        if (success && !settings.isRestoreNotifyOnSuccess()) return;
        if (!success && !settings.isRestoreNotifyOnFailure()) return;

        if (!Boolean.TRUE.equals(settings.getEmailConnectionValid())) {
            log.debug("Restore notification skipped for record {} - email is not connected", record.getId());
            return;
        }

        String recipients = settings.getNotificationRecipients();
        if (recipients == null || recipients.isBlank()) {
            log.debug("Restore notification skipped for record {} - no recipients configured", record.getId());
            return;
        }

        EmailClientConfig config = appSettingsService.buildEmailConfig();
        if (config == null) {
            log.debug("Restore notification skipped for record {} - email config unavailable", record.getId());
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
            builder.subject("Backup Manager — Restore succeeded: " + record.getTargetDatabaseName());
            builder.body(String.format(
                    "Restore completed successfully.\n\nTarget database: %s\nSource database: %s\nContainer: %s\nDuration: %d ms",
                    record.getTargetDatabaseName(), record.getSourceDatabaseName(),
                    record.getContainerId(), record.getDurationMs()));
        } else {
            builder.subject("Backup Manager — Restore failed: " + record.getTargetDatabaseName());
            builder.body(String.format(
                    "Restore failed.\n\nTarget database: %s\nSource database: %s\nContainer: %s\nDuration: %d ms\nError: %s",
                    record.getTargetDatabaseName(), record.getSourceDatabaseName(),
                    record.getContainerId(), record.getDurationMs(),
                    errorMessage != null ? errorMessage : "unknown error"));
        }

        emailService.send(config, builder.build());
    }
}

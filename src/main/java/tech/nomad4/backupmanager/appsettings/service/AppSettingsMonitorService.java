package tech.nomad4.backupmanager.appsettings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;
import tech.nomad4.backupmanager.isolate.email.dto.EmailClientConfig;
import tech.nomad4.backupmanager.isolate.email.dto.EmailMessage;
import tech.nomad4.backupmanager.isolate.email.dto.EmailResult;
import tech.nomad4.backupmanager.isolate.email.service.EmailService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodically re-validates all configured integrations and sends a single
 * email notification when failures are detected.
 *
 * <h3>Schedule</h3>
 * <p>Runs every 15 minutes. Validation results are persisted by
 * {@link AppSettingsValidationService}.</p>
 *
 * <h3>Notification rules</h3>
 * <ul>
 *   <li>A notification is sent only when at least one <em>enabled</em> integration
 *       has a {@code false} connection status after the check.</li>
 *   <li>Email must itself be working ({@code emailConnectionValid == true}) to
 *       be able to deliver the notification.</li>
 *   <li>At most one notification per {@link #NOTIFICATION_COOLDOWN} is sent to
 *       avoid inbox spam. The cooldown is stored in memory only - it resets on
 *       application restart.</li>
 * </ul>
 *
 * <h3>No-circular-dependency note</h3>
 * <p>This service depends on both {@link AppSettingsService} (for email config)
 * and {@link AppSettingsValidationService} (for running checks). Neither of
 * those depends on this service, so there is no cycle.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingsMonitorService {

    /** Minimum time between consecutive failure notifications. */
    private static final Duration NOTIFICATION_COOLDOWN = Duration.ofHours(1);

    private final AppSettingsService appSettingsService;
    private final AppSettingsValidationService validationService;
    private final EmailService emailService;

    /** Timestamp of the last notification sent. {@code null} = never sent. */
    private volatile Instant lastNotificationAt = null;

    // -------------------------------------------------------------------------
    // Scheduled entry point
    // -------------------------------------------------------------------------

    /**
     * Scheduler tick - runs every 5 minutes.
     * <ol>
     *   <li>Re-validates all integrations and persists the results.</li>
     *   <li>Collects the names of enabled integrations whose check failed.</li>
     *   <li>If any failures exist, tries to send an email notification
     *       (subject to email availability and cooldown).</li>
     * </ol>
     */
    @Scheduled(fixedRate = 900_000)
    public void monitor() {
        AppSettings settings = appSettingsService.get();
        settings = validationService.validate(settings);

        List<String> failures = collectFailures(settings);
        if (failures.isEmpty()) {
            return;
        }

        log.warn("Integration monitor found {} failure(s): {}", failures.size(), failures);

        if (!Boolean.TRUE.equals(settings.getEmailConnectionValid())) {
            log.warn("Cannot send failure notification - email integration is not working");
            return;
        }

        if (isCooldownActive()) {
            log.debug("Failure notification skipped - cooldown active (last sent: {})", lastNotificationAt);
            return;
        }

        sendNotification(settings, failures);
        lastNotificationAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the display names of enabled integrations whose connection check
     * is currently {@code false}. Integrations that are disabled or not yet
     * checked ({@code null}) are excluded.
     */
    private List<String> collectFailures(AppSettings settings) {
        List<String> failures = new ArrayList<>();

        if (settings.isEmailEnabled() && Boolean.FALSE.equals(settings.getEmailConnectionValid())) {
            failures.add("Email (SMTP)");
        }
        if (settings.isAwsEnabled() && Boolean.FALSE.equals(settings.getAwsConnectionValid())) {
            failures.add("AWS S3");
        }
        if (settings.isHeartbeatEnabled() && Boolean.FALSE.equals(settings.getHeartbeatConnectionValid())) {
            failures.add("Heartbeat");
        }

        return failures;
    }

    private boolean isCooldownActive() {
        return lastNotificationAt != null
                && Duration.between(lastNotificationAt, Instant.now()).compareTo(NOTIFICATION_COOLDOWN) < 0;
    }

    private void sendNotification(AppSettings settings, List<String> failures) {
        String recipients = settings.getNotificationRecipients();
        if (recipients == null || recipients.isBlank()) {
            log.warn("Cannot send failure notification - no notification recipients configured");
            return;
        }

        EmailClientConfig config = appSettingsService.buildEmailConfig();
        if (config == null) {
            log.warn("Cannot send failure notification - email config is unavailable");
            return;
        }

        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .subject("Backup Manager \u2014 Integration errors detected");

        for (String recipient : recipients.split(",")) {
            String trimmed = recipient.trim();
            if (!trimmed.isBlank()) {
                builder.to(trimmed);
            }
        }

        StringBuilder body = new StringBuilder("The following integrations are currently failing:\n\n");
        for (String failure : failures) {
            body.append("  \u2022 ").append(failure).append("\n");
        }
        body.append("\nPlease review your settings in Backup Manager.");

        EmailResult result = emailService.send(config, builder.body(body.toString()).build());

        if (result.isSuccess()) {
            log.info("Integration failure notification sent to: {}", recipients);
        } else {
            log.warn("Failed to send integration failure notification: {}", result.getErrorMessage());
        }
    }
}

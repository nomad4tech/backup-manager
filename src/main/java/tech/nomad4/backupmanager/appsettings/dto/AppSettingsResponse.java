package tech.nomad4.backupmanager.appsettings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;

import java.time.LocalDateTime;

/**
 * Read-only view of global application settings.
 * <p>
 * Sensitive credentials ({@code emailPassword}, {@code awsSecretKey}) are never
 * included. Instead, {@code emailPasswordConfigured} and {@code awsSecretKeyConfigured}
 * indicate whether a value has been stored.
 * </p>
 */
@Getter
@Builder
@Schema(description = "Global application settings")
public class AppSettingsResponse {

    // -------------------------------------------------------------------------
    // Email
    // -------------------------------------------------------------------------

    private final boolean emailEnabled;
    private final String emailHost;
    private final Integer emailPort;
    private final String emailUsername;
    private final String emailFrom;
    private final boolean emailSsl;
    private final boolean emailStartTls;
    private final int emailTimeoutMs;

    @Schema(description = "True if an email password has been configured (the value itself is not returned)")
    private final boolean emailPasswordConfigured;

    @Schema(description = "Last SMTP connection check result: true = ok, false = failed, null = not checked yet")
    private final Boolean emailConnectionValid;

    // -------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------

    private final boolean notifyOnSuccess;
    private final boolean notifyOnFailure;
    private final String notificationRecipients;

    // -------------------------------------------------------------------------
    // AWS / S3
    // -------------------------------------------------------------------------

    private final boolean awsEnabled;
    private final String awsBucketName;
    private final String awsRegion;
    private final String awsAccessKey;
    private final String awsEndpoint;
    private final boolean awsPathStyleAccess;
    private final String awsDestinationDirectory;

    @Schema(description = "True if an AWS secret key has been configured (the value itself is not returned)")
    private final boolean awsSecretKeyConfigured;

    @Schema(description = "Last S3 connection check result: true = ok, false = failed, null = not checked yet")
    private final Boolean awsConnectionValid;

    // -------------------------------------------------------------------------
    // Heartbeat
    // -------------------------------------------------------------------------

    private final boolean heartbeatEnabled;
    private final String heartbeatUrl;
    private final int heartbeatIntervalSeconds;

    @Schema(description = "Last heartbeat URL ping result: true = ok, false = failed, null = not checked yet")
    private final Boolean heartbeatConnectionValid;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    private final LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static AppSettingsResponse from(AppSettings s) {
        return AppSettingsResponse.builder()
                // Email
                .emailEnabled(s.isEmailEnabled())
                .emailHost(s.getEmailHost())
                .emailPort(s.getEmailPort())
                .emailUsername(s.getEmailUsername())
                .emailFrom(s.getEmailFrom())
                .emailSsl(s.isEmailSsl())
                .emailStartTls(s.isEmailStartTls())
                .emailTimeoutMs(s.getEmailTimeoutMs())
                .emailPasswordConfigured(s.getEmailPassword() != null && !s.getEmailPassword().isBlank())
                .emailConnectionValid(s.getEmailConnectionValid())
                // Notifications
                .notifyOnSuccess(s.isNotifyOnSuccess())
                .notifyOnFailure(s.isNotifyOnFailure())
                .notificationRecipients(s.getNotificationRecipients())
                // AWS
                .awsEnabled(s.isAwsEnabled())
                .awsBucketName(s.getAwsBucketName())
                .awsRegion(s.getAwsRegion())
                .awsAccessKey(s.getAwsAccessKey())
                .awsEndpoint(s.getAwsEndpoint())
                .awsPathStyleAccess(s.isAwsPathStyleAccess())
                .awsDestinationDirectory(s.getAwsDestinationDirectory())
                .awsSecretKeyConfigured(s.getAwsSecretKey() != null && !s.getAwsSecretKey().isBlank())
                .awsConnectionValid(s.getAwsConnectionValid())
                // Heartbeat
                .heartbeatEnabled(s.isHeartbeatEnabled())
                .heartbeatUrl(s.getHeartbeatUrl())
                .heartbeatIntervalSeconds(s.getHeartbeatIntervalSeconds())
                .heartbeatConnectionValid(s.getHeartbeatConnectionValid())
                // Audit
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}

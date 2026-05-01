package tech.nomad4.backupmanager.appsettings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request body for updating global application settings.
 * <p>
 * Sensitive fields ({@code emailPassword}, {@code awsSecretKey}) are optional:
 * if omitted ({@code null}), the existing stored value is preserved. Send an
 * explicit empty string {@code ""} to clear a previously configured credential.
 * </p>
 */
@Data
@Schema(description = "Global application settings")
public class AppSettingsRequest {

    // -------------------------------------------------------------------------
    // Email
    // -------------------------------------------------------------------------

    @Schema(description = "Enable SMTP email sending", example = "true")
    private boolean emailEnabled;

    @Schema(description = "SMTP server hostname or IP", example = "smtp.example.com")
    private String emailHost;

    @Min(value = 1, message = "emailPort must be between 1 and 65535")
    @Max(value = 65535, message = "emailPort must be between 1 and 65535")
    @Schema(description = "SMTP server port", example = "587")
    private Integer emailPort;

    @Schema(description = "SMTP authentication username", example = "user@example.com")
    private String emailUsername;

    @Schema(description = "SMTP authentication password. Omit to keep the existing value; send empty string to clear.",
            example = "secret")
    private String emailPassword;

    @Schema(description = "Sender address shown in the From header",
            example = "Backup Manager <noreply@example.com>")
    private String emailFrom;

    @Schema(description = "Use SSL/TLS from connection start (SMTPS, port 465)", example = "false")
    private boolean emailSsl;

    @Schema(description = "Upgrade connection to TLS via STARTTLS (port 587)", example = "true")
    private boolean emailStartTls;

    @Min(value = 1000, message = "emailTimeoutMs must be at least 1000")
    @Schema(description = "Socket timeout in milliseconds", example = "10000")
    private Integer emailTimeoutMs;

    // -------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------

    @Schema(description = "Send email notification when a backup succeeds", example = "false")
    private boolean notifyOnSuccess;

    @Schema(description = "Send email notification when a backup fails", example = "true")
    private boolean notifyOnFailure;

    @Schema(description = "Comma-separated recipient email addresses for backup notifications",
            example = "ops@example.com,admin@example.com")
    private String notificationRecipients;

    @Schema(description = "Send email notification when a restore succeeds", example = "true")
    private boolean restoreNotifyOnSuccess = true;

    @Schema(description = "Send email notification when a restore fails", example = "true")
    private boolean restoreNotifyOnFailure = true;

    // -------------------------------------------------------------------------
    // AWS / S3
    // -------------------------------------------------------------------------

    @Schema(description = "Enable automatic backup upload to S3 after each successful backup",
            example = "false")
    private boolean awsEnabled;

    @Schema(description = "S3 bucket name", example = "my-backups")
    private String awsBucketName;

    @Schema(description = "AWS region (required even for S3-compatible services)", example = "us-east-1")
    private String awsRegion;

    @Schema(description = "AWS access key ID", example = "AKIAIOSFODNN7EXAMPLE")
    private String awsAccessKey;

    @Schema(description = "AWS secret access key. Omit to keep the existing value; send empty string to clear.",
            example = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
    private String awsSecretKey;

    @Schema(description = "Custom endpoint for S3-compatible services (Yandex Cloud, MinIO). " +
            "Null or empty means use standard AWS S3.",
            example = "https://storage.yandexcloud.net")
    private String awsEndpoint;

    @Schema(description = "Use path-style access (/<bucket>/<key>) instead of virtual-hosted style. " +
            "Required for MinIO and some other S3-compatible services.",
            example = "false")
    private boolean awsPathStyleAccess;

    @Schema(description = "Base directory prefix in the bucket for all uploads. " +
            "Backups are stored as <directory>/<taskName>/filename.",
            example = "backups/prod")
    private String awsDestinationDirectory;

    // -------------------------------------------------------------------------
    // Heartbeat
    // -------------------------------------------------------------------------

    @Schema(description = "Enable periodic heartbeat pings to an uptime-monitoring URL",
            example = "false")
    private boolean heartbeatEnabled;

    @Schema(description = "URL to send HTTP GET requests to (Uptime Kuma, healthchecks.io, Cronitor, etc.)",
            example = "https://uptime.example.com/api/push/xxxx")
    private String heartbeatUrl;

    @Min(value = 30, message = "heartbeatIntervalSeconds must be at least 30")
    @Schema(description = "Interval between heartbeat pings in seconds. Minimum: 30.",
            example = "60")
    private Integer heartbeatIntervalSeconds;
}

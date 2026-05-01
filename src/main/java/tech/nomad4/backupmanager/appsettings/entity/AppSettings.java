package tech.nomad4.backupmanager.appsettings.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Global application settings - a singleton entity.
 * <p>
 * There is always exactly one row in the {@code app_settings} table with
 * {@code id = 1}. The service creates it with sensible defaults on first access.
 * </p>
 *
 * <h3>Sections</h3>
 * <ul>
 *   <li><b>Email</b> - SMTP client for sending notification emails</li>
 *   <li><b>Notifications</b> - which events trigger an email and who receives it</li>
 *   <li><b>AWS</b> - S3-compatible bucket for off-site backup uploads</li>
 *   <li><b>Heartbeat</b> - periodic ping to an uptime-monitoring URL</li>
 * </ul>
 *
 * <p>Sensitive fields ({@code emailPassword}, {@code awsSecretKey}) are stored as-is.
 * They are never returned by the REST API - the response DTO exposes only a
 * {@code *Configured} boolean flag instead.</p>
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
public class AppSettings {

    /** Always 1 - enforces singleton semantics. */
    @Id
    private Long id = 1L;

    // -------------------------------------------------------------------------
    // Email
    // -------------------------------------------------------------------------

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "email_host", length = 255)
    private String emailHost;

    @Column(name = "email_port")
    private Integer emailPort;

    @Column(name = "email_username", length = 255)
    private String emailUsername;

    /** Stored in plain text. Never exposed via the API. */
    @Column(name = "email_password", length = 500)
    private String emailPassword;

    @Column(name = "email_from", length = 255)
    private String emailFrom;

    @Column(name = "email_ssl", nullable = false)
    private boolean emailSsl = false;

    @Column(name = "email_start_tls", nullable = false)
    private boolean emailStartTls = false;

    /** Socket timeout in milliseconds. */
    @Column(name = "email_timeout_ms", nullable = false)
    private int emailTimeoutMs = 10_000;

    // -------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------

    /** Send an email notification when a backup completes successfully. */
    @Column(name = "notify_on_success", nullable = false)
    private boolean notifyOnSuccess = false;

    /** Send an email notification when a backup fails. */
    @Column(name = "notify_on_failure", nullable = false)
    private boolean notifyOnFailure = true;

    /**
     * Comma-separated list of recipient email addresses for backup notifications.
     * Example: {@code "ops@example.com,admin@example.com"}.
     */
    @Column(name = "notification_recipients", length = 1000)
    private String notificationRecipients;

    /** Send an email notification when a restore completes successfully. */
    @Getter(AccessLevel.NONE)
    @Column(name = "restore_notify_on_success")
    private Boolean restoreNotifyOnSuccess = true;

    /** Send an email notification when a restore fails. */
    @Getter(AccessLevel.NONE)
    @Column(name = "restore_notify_on_failure")
    private Boolean restoreNotifyOnFailure = true;

    public boolean isRestoreNotifyOnSuccess() {
        return restoreNotifyOnSuccess != null ? restoreNotifyOnSuccess : true;
    }

    public boolean isRestoreNotifyOnFailure() {
        return restoreNotifyOnFailure != null ? restoreNotifyOnFailure : true;
    }

    // -------------------------------------------------------------------------
    // AWS / S3
    // -------------------------------------------------------------------------

    @Column(name = "aws_enabled", nullable = false)
    private boolean awsEnabled = false;

    @Column(name = "aws_bucket_name", length = 255)
    private String awsBucketName;

    @Column(name = "aws_region", length = 100)
    private String awsRegion;

    @Column(name = "aws_access_key", length = 255)
    private String awsAccessKey;

    /** Stored in plain text. Never exposed via the API. */
    @Column(name = "aws_secret_key", length = 500)
    private String awsSecretKey;

    /**
     * Custom endpoint URL for S3-compatible services (Yandex Cloud, MinIO, etc.).
     * {@code null} means use the standard AWS S3 endpoint.
     */
    @Column(name = "aws_endpoint", length = 500)
    private String awsEndpoint;

    /** Enable path-style access ({@code /<bucket>/<key>}) instead of virtual-hosted style. */
    @Column(name = "aws_path_style_access", nullable = false)
    private boolean awsPathStyleAccess = false;

    /**
     * Base directory prefix inside the bucket for all backup uploads.
     * Example: {@code "backups/prod"} → objects will be stored as
     * {@code "backups/prod/<taskName>/filename.sql"}.
     */
    @Column(name = "aws_destination_directory", length = 500)
    private String awsDestinationDirectory;

    // -------------------------------------------------------------------------
    // Connection status - updated by AppSettingsValidationService
    // -------------------------------------------------------------------------

    /**
     * {@code true} = last AWS connection check passed,
     * {@code false} = last check failed,
     * {@code null} = never checked (disabled or not yet configured).
     */
    @Column(name = "aws_connection_valid")
    private Boolean awsConnectionValid;

    /**
     * {@code true} = last SMTP connection check passed,
     * {@code false} = last check failed,
     * {@code null} = never checked (disabled or not yet configured).
     */
    @Column(name = "email_connection_valid")
    private Boolean emailConnectionValid;

    /**
     * {@code true} = last heartbeat URL ping returned HTTP 2xx,
     * {@code false} = last ping failed or returned non-2xx,
     * {@code null} = never checked (disabled or URL not configured).
     */
    @Column(name = "heartbeat_connection_valid")
    private Boolean heartbeatConnectionValid;

    // -------------------------------------------------------------------------
    // Heartbeat
    // -------------------------------------------------------------------------

    @Column(name = "heartbeat_enabled", nullable = false)
    private boolean heartbeatEnabled = false;

    /**
     * URL to send a periodic HTTP GET request to (e.g. Uptime Kuma push URL,
     * healthchecks.io, Cronitor). An external monitoring service is expected
     * to alert if the ping stops arriving.
     */
    @Column(name = "heartbeat_url", length = 1000)
    private String heartbeatUrl;

    /** Interval between heartbeat pings in seconds. Minimum recommended: 30. */
    @Column(name = "heartbeat_interval_seconds", nullable = false)
    private int heartbeatIntervalSeconds = 60;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

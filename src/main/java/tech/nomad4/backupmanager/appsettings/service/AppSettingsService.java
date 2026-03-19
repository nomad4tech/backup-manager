package tech.nomad4.backupmanager.appsettings.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.nomad4.backupmanager.appsettings.dto.AppSettingsRequest;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;
import tech.nomad4.backupmanager.appsettings.repository.AppSettingsRepository;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketConfig;
import tech.nomad4.backupmanager.isolate.email.dto.EmailClientConfig;

/**
 * Manages the singleton {@link AppSettings} record.
 * <p>
 * On first access, default settings are created and persisted automatically.
 * The record always has {@code id = 1}.
 * </p>
 * <p>
 * Also exposes {@link #buildEmailConfig()} and {@link #buildBucketConfig()} so
 * that other services can obtain properly typed config objects without depending
 * on the entity directly.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private final AppSettingsRepository repository;

    @PostConstruct
    @Transactional
    public void init() {
        if (!repository.existsById(1L)) {
            log.info("No app settings found - initializing defaults");
            repository.save(new AppSettings());
        }
    }


    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the current settings, creating and persisting defaults if none exist yet.
     *
     * @return the singleton settings entity
     */
    @Transactional(readOnly = true)
    public AppSettings get() {
        return repository.findById(1L).orElseGet(() -> {
            log.info("No app settings found - initializing defaults");
            return repository.save(new AppSettings());
        });
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Replaces the current settings with the values from {@code request}.
     * <p>
     * Sensitive fields ({@code emailPassword}, {@code awsSecretKey}) are preserved
     * when the corresponding request field is {@code null}. Send an explicit empty
     * string to clear a stored credential.
     * </p>
     *
     * @param request new settings values
     * @return updated settings entity
     */
    @Transactional
    public AppSettings update(AppSettingsRequest request) {
        AppSettings settings = get();
        applyRequest(request, settings);
        AppSettings saved = repository.save(settings);
        log.info("App settings updated");
        return saved;
    }

    // -------------------------------------------------------------------------
    // Config builders - for use by other services
    // -------------------------------------------------------------------------

    /**
     * Builds an {@link EmailClientConfig} from the current settings.
     * Returns {@code null} if email is disabled or host is not configured.
     *
     * @return email client config, or {@code null} if email is not usable
     */
    public EmailClientConfig buildEmailConfig() {
        AppSettings s = get();
        if (!s.isEmailEnabled() || s.getEmailHost() == null || s.getEmailHost().isBlank()) {
            return null;
        }
        return EmailClientConfig.builder()
                .host(s.getEmailHost())
                .port(s.getEmailPort() != null ? s.getEmailPort() : 587)
                .username(s.getEmailUsername())
                .password(s.getEmailPassword())
                .from(s.getEmailFrom() != null ? s.getEmailFrom()
                        : s.getEmailUsername() != null ? s.getEmailUsername()
                        : "backup-manager@localhost")
                .ssl(s.isEmailSsl())
                .startTls(s.isEmailStartTls())
                .timeoutMs(s.getEmailTimeoutMs())
                .build();
    }

    /**
     * Builds a {@link BucketConfig} from the current settings.
     * Returns {@code null} if AWS upload is disabled or bucket name is not configured.
     *
     * @return S3 bucket config, or {@code null} if AWS upload is not usable
     */
    public BucketConfig buildBucketConfig() {
        AppSettings s = get();
        if (!s.isAwsEnabled() || s.getAwsBucketName() == null || s.getAwsBucketName().isBlank()) {
            return null;
        }
        return BucketConfig.builder()
                .bucketName(s.getAwsBucketName())
                .region(s.getAwsRegion())
                .accessKey(s.getAwsAccessKey())
                .secretKey(s.getAwsSecretKey())
                .endpoint(s.getAwsEndpoint())
                .pathStyleAccess(s.isAwsPathStyleAccess())
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void applyRequest(AppSettingsRequest req, AppSettings settings) {
        // Email
        settings.setEmailEnabled(req.isEmailEnabled());
        settings.setEmailHost(req.getEmailHost());
        settings.setEmailPort(req.getEmailPort());
        settings.setEmailUsername(req.getEmailUsername());
        settings.setEmailFrom(req.getEmailFrom());
        settings.setEmailSsl(req.isEmailSsl());
        settings.setEmailStartTls(req.isEmailStartTls());
        if (req.getEmailTimeoutMs() != null) {
            settings.setEmailTimeoutMs(req.getEmailTimeoutMs());
        }
        // Preserve existing password if request omits it
        if (req.getEmailPassword() != null) {
            settings.setEmailPassword(req.getEmailPassword().isBlank() ? null : req.getEmailPassword());
        }

        // Notifications
        settings.setNotifyOnSuccess(req.isNotifyOnSuccess());
        settings.setNotifyOnFailure(req.isNotifyOnFailure());
        settings.setNotificationRecipients(req.getNotificationRecipients());

        // AWS
        settings.setAwsEnabled(req.isAwsEnabled());
        settings.setAwsBucketName(req.getAwsBucketName());
        settings.setAwsRegion(req.getAwsRegion());
        settings.setAwsAccessKey(req.getAwsAccessKey());
        settings.setAwsEndpoint(req.getAwsEndpoint());
        settings.setAwsPathStyleAccess(req.isAwsPathStyleAccess());
        settings.setAwsDestinationDirectory(req.getAwsDestinationDirectory());
        // Preserve existing secret key if request omits it
        if (req.getAwsSecretKey() != null) {
            settings.setAwsSecretKey(req.getAwsSecretKey().isBlank() ? null : req.getAwsSecretKey());
        }

        // Heartbeat
        settings.setHeartbeatEnabled(req.isHeartbeatEnabled());
        settings.setHeartbeatUrl(req.getHeartbeatUrl());
        if (req.getHeartbeatIntervalSeconds() != null) {
            settings.setHeartbeatIntervalSeconds(req.getHeartbeatIntervalSeconds());
        }
    }
}

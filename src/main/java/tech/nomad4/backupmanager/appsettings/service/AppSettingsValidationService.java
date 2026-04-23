package tech.nomad4.backupmanager.appsettings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;
import tech.nomad4.backupmanager.appsettings.repository.AppSettingsRepository;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketCheckResult;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketConfig;
import tech.nomad4.backupmanager.isolate.awsbucket.service.AwsBucketService;
import tech.nomad4.backupmanager.isolate.email.dto.EmailCheckResult;
import tech.nomad4.backupmanager.isolate.email.dto.EmailClientConfig;
import tech.nomad4.backupmanager.isolate.email.service.EmailService;

import java.util.concurrent.TimeUnit;

/**
 * Validates live connectivity for the AWS S3, email, and heartbeat integrations
 * configured in {@link AppSettings}, and persists the check results back to the database.
 * <p>
 * This service is the single place that bridges {@link AppSettings} with the
 * isolated {@code AwsBucketService} and {@code EmailService}. It intentionally
 * does <em>not</em> depend on {@link AppSettingsService} to avoid a circular
 * Spring dependency.
 * </p>
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>If a section is disabled or its primary address field (bucket name /
 *       SMTP host) is blank, the corresponding {@code *ConnectionValid} flag is
 *       reset to {@code null} (= not applicable).</li>
 *   <li>Otherwise a real network check is performed and the flag is set to
 *       {@code true} (reachable) or {@code false} (failed).</li>
 * </ul>
 *
 * <h3>Calling patterns</h3>
 * <ul>
 *   <li>{@link #validate(AppSettings)} - pass the freshly saved entity from
 *       {@code AppSettingsService.update()} to avoid an extra DB read.</li>
 *   <li>{@link #validate()} - no-arg variant for use by a {@code @Scheduled}
 *       task that re-checks connections periodically.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingsValidationService {

    private final AppSettingsRepository repository;
    private final AwsBucketService awsBucketService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates connections for the given settings entity, updates its
     * {@code awsConnectionValid} and {@code emailConnectionValid} flags, and
     * saves the result.
     *
     * @param settings the entity to validate (already persisted, but not yet
     *                 updated with connection status)
     * @return the same entity after the status fields have been saved
     */
    public AppSettings validate(AppSettings settings) {
        checkAws(settings);
        checkEmail(settings);
        checkHeartbeat(settings);
        return repository.save(settings);
    }

    /**
     * Loads the current settings from the database and re-validates connections.
     * <p>
     * Intended for use by a {@code @Scheduled} task. Does nothing if no settings
     * record exists yet.
     * </p>
     */
    public void validate() {
        repository.findById(1L).ifPresent(this::validate);
    }

    // -------------------------------------------------------------------------
    // Private - per-integration checks
    // -------------------------------------------------------------------------

    private void checkAws(AppSettings settings) {
        if (!settings.isAwsEnabled()
                || settings.getAwsBucketName() == null
                || settings.getAwsBucketName().isBlank()) {
            settings.setAwsConnectionValid(null);
            return;
        }

        BucketConfig config = BucketConfig.builder()
                .bucketName(settings.getAwsBucketName())
                .region(settings.getAwsRegion())
                .accessKey(settings.getAwsAccessKey())
                .secretKey(settings.getAwsSecretKey())
                .endpoint(settings.getAwsEndpoint())
                .pathStyleAccess(settings.isAwsPathStyleAccess())
                .destinationDirectory(settings.getAwsDestinationDirectory())
                .build();

        BucketCheckResult result = awsBucketService.checkConnection(config);
        settings.setAwsConnectionValid(result.isReachable());

        if (!result.isReachable()) {
            log.warn("AWS connection check failed: {}", result.getErrorMessage());
        }
    }

    private void checkEmail(AppSettings settings) {
        if (!settings.isEmailEnabled()
                || settings.getEmailHost() == null
                || settings.getEmailHost().isBlank()) {
            settings.setEmailConnectionValid(null);
            return;
        }

        EmailClientConfig config = EmailClientConfig.builder()
                .host(settings.getEmailHost())
                .port(settings.getEmailPort() != null ? settings.getEmailPort() : 587)
                .username(settings.getEmailUsername())
                .password(settings.getEmailPassword())
                .from(settings.getEmailFrom())
                .ssl(settings.isEmailSsl())
                .startTls(settings.isEmailStartTls())
                .timeoutMs(settings.getEmailTimeoutMs())
                .build();

        EmailCheckResult result = EmailService.checkConnection(config);
        settings.setEmailConnectionValid(result.isReachable());

        if (!result.isReachable()) {
            log.warn("Email connection check failed: {}", result.getErrorMessage());
        }
    }

    private void checkHeartbeat(AppSettings settings) {
        if (!settings.isHeartbeatEnabled()
                || settings.getHeartbeatUrl() == null
                || settings.getHeartbeatUrl().isBlank()) {
            settings.setHeartbeatConnectionValid(null);
            return;
        }

        String url = settings.getHeartbeatUrl();
        log.info("Checking heartbeat URL: {}", url);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(
                new Request.Builder().url(url).get().build()
        ).execute()) {
            boolean ok = response.isSuccessful();
            settings.setHeartbeatConnectionValid(ok);
            if (ok) {
                log.info("Heartbeat check passed: url={}, status={}", url, response.code());
            } else {
                log.warn("Heartbeat check failed: url={}, status={}", url, response.code());
            }
        } catch (Exception e) {
            log.warn("Heartbeat check failed: url={}: {}", url, e.getMessage());
            settings.setHeartbeatConnectionValid(false);
        }
    }
}

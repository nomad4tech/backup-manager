package tech.nomad4.backupmanager.appsettings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Sends periodic HTTP GET pings to the configured heartbeat URL.
 * <p>
 * The ping interval is read from {@link AppSettings#getHeartbeatIntervalSeconds()}
 * on every scheduler tick, so changes to the interval take effect immediately
 * without a restart.
 * </p>
 * <p>
 * The scheduler checks every 30 seconds whether a ping is due; the actual ping
 * is sent only when {@code now − lastPingAt ≥ heartbeatIntervalSeconds}.
 * </p>
 * <p>
 * All errors are swallowed and logged - this service never throws.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private final AppSettingsService appSettingsService;

    /** Shared HTTP client - reused across pings to preserve the connection pool. */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build();

    /** Timestamp of the last successfully dispatched ping. {@code null} = never sent. */
    private volatile Instant lastPingAt = null;

    /**
     * Scheduler tick - runs every 30 seconds.
     * Sends a ping only when the configured interval has elapsed since the last one.
     */
    @Scheduled(fixedRate = 30_000)
    public void tick() {
        AppSettings settings = appSettingsService.get();

        if (!settings.isHeartbeatEnabled()
                || settings.getHeartbeatUrl() == null
                || settings.getHeartbeatUrl().isBlank()) {
            lastPingAt = null;
            return;
        }

        long intervalMs = settings.getHeartbeatIntervalSeconds() * 1000L;
        if (lastPingAt != null
                && Duration.between(lastPingAt, Instant.now()).toMillis() < intervalMs) {
            return;
        }

        ping(settings.getHeartbeatUrl());
        lastPingAt = Instant.now();
    }

    private void ping(String url) {
        log.debug("Sending heartbeat ping: {}", url);
        try (Response response = httpClient
                .newCall(new Request.Builder().url(url).get().build())
                .execute()) {
            if (response.isSuccessful()) {
                log.debug("Heartbeat ping OK: url={}, status={}", url, response.code());
            } else {
                log.warn("Heartbeat ping returned non-2xx: url={}, status={}", url, response.code());
            }
        } catch (Exception e) {
            log.warn("Heartbeat ping failed: url={}: {}", url, e.getMessage());
        }
    }
}

package tech.nomad4.backupmanager.appsettings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import okhttp3.OkHttpClient;
import tech.nomad4.backupmanager.appsettings.dto.AppSettingsRequest;
import tech.nomad4.backupmanager.appsettings.dto.AppSettingsResponse;
import tech.nomad4.backupmanager.appsettings.dto.AwsCheckRequest;
import tech.nomad4.backupmanager.appsettings.dto.ConnectionCheckResult;
import tech.nomad4.backupmanager.appsettings.dto.EmailCheckRequest;
import tech.nomad4.backupmanager.appsettings.dto.HeartbeatCheckRequest;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;
import tech.nomad4.backupmanager.appsettings.service.AppSettingsService;
import tech.nomad4.backupmanager.appsettings.service.AppSettingsValidationService;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketCheckResult;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketConfig;
import tech.nomad4.backupmanager.isolate.awsbucket.service.AwsBucketService;
import tech.nomad4.backupmanager.isolate.email.dto.EmailCheckResult;
import tech.nomad4.backupmanager.isolate.email.dto.EmailClientConfig;
import tech.nomad4.backupmanager.isolate.email.service.EmailService;

/**
 * REST controller for reading and updating global application settings.
 * <p>
 * Settings are a singleton - there is no list endpoint and no ID in the path.
 * </p>
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Global application settings: email, AWS S3, heartbeat")
public class AppSettingsController {

    private final AppSettingsService service;
    private final AppSettingsValidationService validationService;
    private final AwsBucketService awsBucketService;
    private final EmailService emailService;

    @Operation(
            summary = "Get current application settings",
            description = "Returns the current global settings. " +
                    "Sensitive credentials are not included - only a *Configured flag indicates whether they are set."
    )
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = AppSettingsResponse.class)))
    @GetMapping
    public ResponseEntity<AppSettingsResponse> get() {
        return ResponseEntity.ok(AppSettingsResponse.from(service.get()));
    }

    @Operation(
            summary = "Update application settings",
            description = "Replaces all settings with the provided values. " +
                    "Omit emailPassword / awsSecretKey to keep the existing stored values. " +
                    "Send an explicit empty string to clear a credential."
    )
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = AppSettingsResponse.class)))
    @ApiResponse(responseCode = "400",
            content = @Content(schema = @Schema(implementation = String.class)))
    @PutMapping
    public ResponseEntity<AppSettingsResponse> update(@Valid @RequestBody AppSettingsRequest request) {
        AppSettings saved = service.update(request);
        AppSettings validated = validationService.validate(saved);
        return ResponseEntity.ok(AppSettingsResponse.from(validated));
    }

    @Operation(
            summary = "Check AWS S3 connectivity",
            description = "Immediately checks S3 connectivity using credentials from the request body. " +
                    "Pass awsSecretKey=null to use the stored secret key from the database."
    )
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = BucketCheckResult.class)))
    @PostMapping("/aws/check")
    public ResponseEntity<BucketCheckResult> checkAws(@RequestBody AwsCheckRequest request) {
        String secretKey = request.getAwsSecretKey() != null
                ? request.getAwsSecretKey()
                : service.get().getAwsSecretKey();

        BucketConfig config = BucketConfig.builder()
                .bucketName(request.getBucketName())
                .region(request.getRegion())
                .accessKey(request.getAccessKey())
                .secretKey(secretKey)
                .endpoint(request.getEndpoint())
                .pathStyleAccess(request.isPathStyleAccess())
                .destinationDirectory(request.getDestinationDirectory())
                .build();

        return ResponseEntity.ok(awsBucketService.checkConnection(config));
    }

    @Operation(summary = "Check email (SMTP) connectivity",
            description = "Checks SMTP connectivity using credentials from the request body. " +
                    "Pass password=null to use the stored password from the database.")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = ConnectionCheckResult.class)))
    @PostMapping("/email/check")
    public ResponseEntity<ConnectionCheckResult> checkEmail(@RequestBody EmailCheckRequest request) {
        String password = request.getPassword() != null
                ? request.getPassword()
                : service.get().getEmailPassword();

        EmailClientConfig config = EmailClientConfig.builder()
                .host(request.getHost())
                .port(request.getPort() != null ? request.getPort() : 587)
                .username(request.getUsername())
                .password(password)
                .from(request.getFrom())
                .ssl(request.isSsl())
                .startTls(request.isStartTls())
                .timeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 10000)
                .build();

        EmailCheckResult result = EmailService.checkConnection(config);
        return ResponseEntity.ok(ConnectionCheckResult.builder()
                .reachable(result.isReachable())
                .errorMessage(result.getErrorMessage())
                .build());
    }

    @Operation(summary = "Check heartbeat URL connectivity",
            description = "Performs an HTTP GET to the given URL and returns whether it responds successfully.")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = ConnectionCheckResult.class)))
    @PostMapping("/heartbeat/check")
    public ResponseEntity<ConnectionCheckResult> checkHeartbeat(@RequestBody HeartbeatCheckRequest request) {
        String url = request.getUrl();
        if (url == null || url.isBlank()) {
            return ResponseEntity.ok(ConnectionCheckResult.builder()
                    .reachable(false)
                    .errorMessage("URL is required")
                    .build());
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        try (okhttp3.Response response = client.newCall(
                new okhttp3.Request.Builder().url(url).get().build()
        ).execute()) {
            boolean ok = response.isSuccessful();
            return ResponseEntity.ok(ConnectionCheckResult.builder()
                    .reachable(ok)
                    .errorMessage(ok ? null : "HTTP " + response.code())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(ConnectionCheckResult.builder()
                    .reachable(false)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

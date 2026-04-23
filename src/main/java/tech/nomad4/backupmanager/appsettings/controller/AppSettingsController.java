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
import tech.nomad4.backupmanager.appsettings.dto.AppSettingsRequest;
import tech.nomad4.backupmanager.appsettings.dto.AppSettingsResponse;
import tech.nomad4.backupmanager.appsettings.dto.AwsCheckRequest;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;
import tech.nomad4.backupmanager.appsettings.service.AppSettingsService;
import tech.nomad4.backupmanager.appsettings.service.AppSettingsValidationService;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketCheckResult;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketConfig;
import tech.nomad4.backupmanager.isolate.awsbucket.service.AwsBucketService;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

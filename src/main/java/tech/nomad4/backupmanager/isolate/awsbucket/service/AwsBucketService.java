package tech.nomad4.backupmanager.isolate.awsbucket.service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketCheckResult;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.BucketConfig;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.UploadCommand;
import tech.nomad4.backupmanager.isolate.awsbucket.dto.UploadResult;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Uploads local files to an S3-compatible bucket.
 * <p>
 * This service is intentionally free of Spring dependencies. It can be
 * instantiated and used in any context - Spring beans, standalone tools,
 * or tests - without a running application context.
 * </p>
 * <p>
 * Supports both AWS S3 and S3-compatible services (Yandex Cloud Object Storage,
 * MinIO, etc.) by accepting a custom endpoint in {@link BucketConfig}.
 * </p>
 * <p>
 * {@link #upload} never throws; it catches all errors and returns an
 * {@link UploadResult} with {@code success = false} and an error message.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AwsBucketService service = new AwsBucketService();
 *
 * UploadResult result = service.upload(UploadCommand.builder()
 *     .config(BucketConfig.builder()
 *         .bucketName("my-backups")
 *         .region("us-east-1")
 *         .accessKey("AKIA...")
 *         .secretKey("secret")
 *         .build())
 *     .localFilePath("/var/backups/dump.sql.gz")
 *     .destinationDirectory("postgres/mydb")
 *     .build());
 *
 * if (result.isSuccess()) {
 *     System.out.println("Uploaded to: " + result.getRemoteKey());
 * } else {
 *     System.err.println("Upload failed: " + result.getErrorMessage());
 * }
 * }</pre>
 */
@Slf4j
public class AwsBucketService {

    /**
     * Uploads the file described by {@code command} to the configured S3 bucket.
     * <p>
     * A new {@link S3Client} is created for each call based on the credentials
     * and endpoint in {@link UploadCommand#getConfig()}. The client is closed
     * automatically after the upload completes or fails.
     * </p>
     *
     * @param command upload parameters: bucket config, local file path, remote directory
     * @return upload result - success/failure, remote key, ETag, size, and timing
     */
    public UploadResult upload(UploadCommand command) {
        LocalDateTime startedAt = LocalDateTime.now();
        String remoteKey = command.resolveRemoteKey();
        BucketConfig config = command.getConfig();

        log.info("Starting upload: localFile={}, bucket={}, remoteKey={}",
                command.getLocalFilePath(), config.getBucketName(), remoteKey);

        Path localPath = Paths.get(command.getLocalFilePath());
        long sizeBytes = readFileSize(localPath);

        try (S3Client s3 = buildClient(config)) {

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(remoteKey)
                    .contentLength(sizeBytes >= 0 ? sizeBytes : null)
                    .build();

            PutObjectResponse response = s3.putObject(request, RequestBody.fromFile(localPath));

            LocalDateTime completedAt = LocalDateTime.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();

            log.info("Upload completed in {} ms: bucket={}, key={}, eTag={}",
                    durationMs, config.getBucketName(), remoteKey, response.eTag());

            return UploadResult.builder()
                    .localFilePath(command.getLocalFilePath())
                    .bucketName(config.getBucketName())
                    .remoteKey(remoteKey)
                    .eTag(response.eTag())
                    .sizeBytes(sizeBytes)
                    .durationMs(durationMs)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .success(true)
                    .build();

        } catch (Exception e) {
            LocalDateTime completedAt = LocalDateTime.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();

            log.error("Upload failed: localFile={}, bucket={}, key={}: {}",
                    command.getLocalFilePath(), config.getBucketName(), remoteKey, e.getMessage(), e);

            return UploadResult.builder()
                    .localFilePath(command.getLocalFilePath())
                    .bucketName(config.getBucketName())
                    .remoteKey(remoteKey)
                    .sizeBytes(sizeBytes)
                    .durationMs(durationMs)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Checks whether the bucket described by {@code config} is reachable and the
     * credentials are accepted by the server.
     * <p>
     * Performs a lightweight {@code HeadBucket} request - no data is transferred
     * and no side effects occur on the bucket. The check covers:
     * <ul>
     *   <li>network reachability of the endpoint</li>
     *   <li>validity of the access key and secret key</li>
     *   <li>existence of the bucket</li>
     *   <li>caller's permission to access the bucket</li>
     * </ul>
     * </p>
     * <p>
     * This method never throws. All errors are captured and returned as a
     * {@link BucketCheckResult} with {@code reachable = false}.
     * </p>
     *
     * @param config bucket connection settings to verify
     * @return check result - reachable/unreachable and error message on failure
     */
    public BucketCheckResult checkConnection(BucketConfig config) {
        log.info("Checking connection: bucket={}, region={}, endpoint={}",
                config.getBucketName(), config.getRegion(), config.getEndpoint());

        String probeKey = (config.getDestinationDirectory() != null && !config.getDestinationDirectory().isBlank())
                ? config.getDestinationDirectory() + "/backup-manager-probe.tmp"
                : "backup-manager-probe.tmp";

        try (S3Client s3 = buildClient(config)) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(config.getBucketName())
                            .key(probeKey)
                            .build(),
                    RequestBody.empty()
            );

            log.info("Connection check passed: bucket={}", config.getBucketName());

            return BucketCheckResult.builder()
                    .bucketName(config.getBucketName())
                    .reachable(true)
                    .build();

        } catch (Exception e) {
            log.warn("Connection check failed: bucket={}: {}",
                    config.getBucketName(), e.getMessage());

            return BucketCheckResult.builder()
                    .bucketName(config.getBucketName())
                    .reachable(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds an {@link S3Client} from the given configuration.
     * <p>
     * When {@link BucketConfig#getEndpoint()} is non-null, a custom endpoint
     * is configured (required for Yandex Cloud, MinIO, and other S3-compatible
     * services). Path-style access is enabled via {@link BucketConfig#isPathStyleAccess()}.
     * </p>
     *
     * @param config bucket connection settings
     * @return a ready-to-use S3 client (caller must close it)
     */
    private S3Client buildClient(BucketConfig config) {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())
        );

        var s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(config.isPathStyleAccess())
                .build();

        var builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config);

        if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }

        return builder.build();
    }

    /**
     * Returns the file size in bytes, or {@code -1} if the size cannot be determined.
     *
     * @param path path to the local file
     * @return file size in bytes, or {@code -1}
     */
    private long readFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            log.warn("Could not read file size for {}: {}", path, e.getMessage());
            return -1;
        }
    }
}

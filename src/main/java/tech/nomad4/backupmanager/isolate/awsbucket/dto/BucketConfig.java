package tech.nomad4.backupmanager.isolate.awsbucket.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Connection and authentication settings for an S3-compatible bucket.
 * <p>
 * Supports both AWS S3 and S3-compatible services (Yandex Cloud Object Storage,
 * MinIO, etc.) via an optional custom {@link #endpoint}.
 * </p>
 * <p>
 * When {@link #endpoint} is {@code null}, the standard AWS S3 endpoint for the
 * given {@link #region} is used. Set {@link #pathStyleAccess} to {@code true}
 * for services that require path-style addressing (e.g. MinIO).
 * </p>
 */
@Data
@Builder
public class BucketConfig {

    /**
     * Target bucket name.
     */
    private String bucketName;

    /**
     * AWS region (e.g. {@code "us-east-1"}, {@code "ru-central1"}).
     * Required even when a custom endpoint is provided.
     */
    private String region;

    /**
     * Access key ID (AWS access key or equivalent for S3-compatible services).
     */
    private String accessKey;

    /**
     * Secret access key corresponding to {@link #accessKey}.
     */
    private String secretKey;

    /**
     * Custom endpoint URL for S3-compatible services.
     * Example: {@code "https://storage.yandexcloud.net"} or {@code "http://minio:9000"}.
     * Set to {@code null} to use the default AWS S3 endpoint.
     */
    private String endpoint;

    /**
     * Whether to use path-style access ({@code /<bucket>/<key>}) instead of
     * virtual-hosted-style access ({@code <bucket>.s3.<region>.amazonaws.com/<key>}).
     * Required for MinIO and some other S3-compatible services.
     */
    private boolean pathStyleAccess;

    /**
     * Optional prefix directory inside the bucket.
     * When set, probe and upload keys are placed under this directory.
     */
    @Builder.Default
    private String destinationDirectory = null;
}

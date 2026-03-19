package tech.nomad4.backupmanager.isolate.awsbucket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a bucket connectivity and credentials check.
 * <p>
 * Returned by
 * {@link tech.nomad4.backupmanager.isolate.awsbucket.service.AwsBucketService#checkConnection}
 * to indicate whether the provided {@link BucketConfig} is reachable and valid.
 * </p>
 * <p>
 * Check {@link #isReachable()} first. On failure {@link #errorMessage} contains
 * a human-readable description of the problem (wrong credentials, unknown bucket,
 * network error, etc.).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketCheckResult {

    /** Name of the bucket that was checked. */
    private String bucketName;

    /** {@code true} if the bucket is reachable and the credentials are accepted. */
    private boolean reachable;

    /** Human-readable error description; {@code null} on success. */
    private String errorMessage;
}

package tech.nomad4.backupmanager.isolate.awsbucket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outcome of a single file upload to an S3-compatible bucket.
 * <p>
 * Check {@link #isSuccess()} first. On success, {@link #remoteKey} and
 * {@link #eTag} are populated. On failure, {@link #errorMessage} describes
 * the cause; {@link #remoteKey} is still set to the intended key for
 * diagnostic purposes.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {

    /** Absolute local path of the file that was (or was attempted to be) uploaded. */
    private String localFilePath;

    /** Target bucket name. */
    private String bucketName;

    /**
     * S3 object key of the uploaded file, e.g. {@code "postgres/mydb/dump.sql.gz"}.
     * Set to the intended key even when the upload fails.
     */
    private String remoteKey;

    /**
     * ETag returned by the server upon successful upload (typically an MD5 hex string
     * for single-part uploads). {@code null} on failure.
     */
    private String eTag;

    /** Size of the uploaded file in bytes. {@code -1} if the file could not be read. */
    private long sizeBytes;

    private long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private boolean success;

    /** Human-readable error description; {@code null} on success. */
    private String errorMessage;
}

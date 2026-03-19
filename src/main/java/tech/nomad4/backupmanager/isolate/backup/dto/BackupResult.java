package tech.nomad4.backupmanager.isolate.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.nomad4.backupmanager.isolate.storage.service.StorageService;

import java.time.LocalDateTime;

/**
 * Outcome of a backup execution attempt.
 * <p>
 * Represents only the execution result - whether the dump process succeeded,
 * how long it took, and where the output file was written. File size, checksum,
 * and other storage-level metadata are intentionally absent; the caller obtains
 * those via {@link StorageService}.
 * </p>
 * <p>
 * Check {@link #isSuccess()} first. On failure {@link #errorMessage} describes
 * the cause; {@link #filePath} may still be set even on failure (pointing to a
 * partial or missing file).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupResult {

    /**
     * Absolute path of the output file that was (or was attempted to be) written.
     * Use this path with {@code StorageService} to retrieve file metadata.
     */
    private String filePath;

    private long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private boolean success;

    /** Human-readable error description; {@code null} on success. */
    private String errorMessage;
}

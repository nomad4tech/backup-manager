package tech.nomad4.backupmanager.isolate.restore.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Outcome of a restore execution attempt.
 * <p>
 * Check {@link #isSuccess()} first. On failure {@link #errorMessage} describes
 * the cause; {@link #inputFilePath} may still be set even on failure.
 * </p>
 */
@Builder
@Getter
public class RestoreResult {

    /** Absolute path of the backup file that was (or was attempted to be) restored from. */
    private String inputFilePath;

    private long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private boolean success;

    /** Human-readable error description; {@code null} on success. */
    private String errorMessage;
}

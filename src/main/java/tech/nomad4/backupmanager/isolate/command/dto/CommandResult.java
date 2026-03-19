package tech.nomad4.backupmanager.isolate.command.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of a command executed inside a Docker container.
 * <p>
 * Contains the captured stdout/stderr streams, the process exit code,
 * timing information, and the final execution state.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResult {

    private String operationId;
    private int exitCode;
    private String stdout;
    private String stderr;
    private ExecutionState state;
    private Long executionTimeMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /**
     * Returns {@code true} if the command completed with exit code 0.
     */
    public boolean isSuccess() {
        return exitCode == 0 && state == ExecutionState.COMPLETED;
    }

    /**
     * Returns a human-readable error description, or a generic message
     * when no specific error text is available.
     */
    public String getError() {
        if (stderr != null && !stderr.isEmpty()) {
            return stderr;
        }
        return switch (state) {
            case TIMEOUT -> "Operation timed out";
            case CANCELLED -> "Operation was cancelled";
            default -> "Unknown error (exit code " + exitCode + ")";
        };
    }
}

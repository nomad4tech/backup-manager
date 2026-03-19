package tech.nomad4.backupmanager.isolate.command.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Status snapshot of a running or completed asynchronous operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationStatus {

    private String operationId;
    private String containerId;
    private String containerName;
    private ExecutionState state;
    private String commandSummary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private String errorMessage;

    /**
     * Returns {@code true} if the operation has reached a terminal state.
     */
    public boolean isComplete() {
        return state == ExecutionState.COMPLETED
                || state == ExecutionState.FAILED
                || state == ExecutionState.CANCELLED
                || state == ExecutionState.TIMEOUT;
    }

    /**
     * Returns {@code true} if the operation is currently executing.
     */
    public boolean isRunning() {
        return state == ExecutionState.RUNNING;
    }

    /**
     * Returns {@code true} if the operation is waiting to start.
     */
    public boolean isQueued() {
        return state == ExecutionState.QUEUED;
    }
}

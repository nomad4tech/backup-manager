package tech.nomad4.backupmanager.isolate.command.executor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.command.dto.CommandResult;
import tech.nomad4.backupmanager.isolate.command.dto.DatabaseCommand;
import tech.nomad4.backupmanager.isolate.command.dto.ExecutionState;
import tech.nomad4.backupmanager.isolate.command.dto.OperationStatus;
import tech.nomad4.backupmanager.isolate.command.exception.CommandExecutionException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages asynchronous command execution with status tracking and TTL cleanup.
 * <p>
 * Operations are submitted as {@link Callable} instances so the manager stays
 * decoupled from command-building logic. Each operation is tracked by a unique
 * ID and its status can be polled until completion. Completed results are
 * automatically cleaned up after their configured TTL expires.
 * </p>
 */
@Slf4j
public class AsyncOperationManager implements AutoCloseable {

    private final long defaultTtl;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, TrackedOperation> operations = new ConcurrentHashMap<>();

    public AsyncOperationManager(int maxConcurrent, long defaultTtl) {
        this.executorService = Executors.newFixedThreadPool(maxConcurrent);
        this.defaultTtl = defaultTtl;
        log.info("AsyncOperationManager initialized with max {} concurrent operations", maxConcurrent);
    }

    /**
     * Submits an operation for asynchronous execution.
     *
     * @param containerId the container this operation targets
     * @param command     the original command descriptor (used for summary and TTL)
     * @param task        the callable that performs the actual execution
     * @return the unique operation ID
     */
    public String submit(String containerId,
                         DatabaseCommand command,
                         Callable<CommandResult> task) {

        String operationId = UUID.randomUUID().toString();

        OperationStatus status = OperationStatus.builder()
                .operationId(operationId)
                .containerId(containerId)
                .state(ExecutionState.QUEUED)
                .commandSummary(command.getSummary())
                .startedAt(LocalDateTime.now())
                .build();

        Future<CommandResult> future = executorService.submit(() -> {
            updateState(operationId, ExecutionState.RUNNING);
            try {
                CommandResult result = task.call();
                result.setOperationId(operationId);

                ExecutionState finalState = result.isSuccess()
                        ? ExecutionState.COMPLETED
                        : result.getState();
                updateState(operationId, finalState, result.getStderr());
                return result;

            } catch (Exception e) {
                updateState(operationId, ExecutionState.FAILED, e.getMessage());
                throw new CommandExecutionException("Async execution failed", e);
            }
        });

        operations.put(operationId, new TrackedOperation(operationId, containerId, command, future, status));
        log.info("Operation {} queued for container {}", operationId, containerId);
        return operationId;
    }

    /**
     * Returns the current status of an operation.
     *
     * @param operationId the operation ID
     * @return current status
     * @throws CommandExecutionException if the operation is not found
     */
    public OperationStatus getStatus(String operationId) {
        TrackedOperation op = requireOperation(operationId);
        return op.getStatus();
    }

    /**
     * Returns the result of a completed operation.
     *
     * @param operationId the operation ID
     * @param blocking    if {@code true}, blocks until the operation completes
     * @return the command result, or {@code null} if non-blocking and not yet complete
     * @throws CommandExecutionException if the operation is not found or retrieval fails
     */
    public CommandResult getResult(String operationId, boolean blocking) {
        TrackedOperation op = requireOperation(operationId);

        if (blocking) {
            try {
                return op.getFuture().get();
            } catch (ExecutionException e) {
                throw new CommandExecutionException("Operation failed: " + e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CommandExecutionException("Interrupted while waiting for result", e);
            } catch (CancellationException e) {
                throw new CommandExecutionException("Operation was cancelled", e);
            }
        }

        if (op.getFuture().isDone()) {
            try {
                return op.getFuture().get();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Cancels a running or queued operation.
     *
     * @param operationId the operation ID
     * @return {@code true} if the operation was cancelled, {@code false} if already complete
     */
    public boolean cancel(String operationId) {
        TrackedOperation op = operations.get(operationId);
        if (op == null || op.getStatus().isComplete()) {
            return false;
        }

        boolean cancelled = op.getFuture().cancel(true);
        if (cancelled) {
            updateState(operationId, ExecutionState.CANCELLED);
        }
        return cancelled;
    }

    /**
     * Returns the status of all tracked operations.
     */
    public List<OperationStatus> listAll() {
        return operations.values().stream()
                .map(TrackedOperation::getStatus)
                .collect(Collectors.toList());
    }

    /**
     * Returns the status of operations targeting a specific container.
     *
     * @param containerId the container ID to filter by
     */
    public List<OperationStatus> listByContainer(String containerId) {
        return operations.values().stream()
                .filter(op -> op.getContainerId().equals(containerId))
                .map(TrackedOperation::getStatus)
                .collect(Collectors.toList());
    }

    /**
     * Removes completed operations whose TTL has expired.
     * Call this periodically from the application layer (e.g., a scheduled task).
     */
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        int removed = 0;

        var iterator = operations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            TrackedOperation op = entry.getValue();

            if (!op.getStatus().isComplete()) {
                continue;
            }

            LocalDateTime completedAt = op.getStatus().getCompletedAt();
            if (completedAt == null) {
                continue;
            }

            long ttl = op.getCommand().getResultTtlSeconds() != null
                    ? op.getCommand().getResultTtlSeconds()
                    : defaultTtl;

            if (now.isAfter(completedAt.plusSeconds(ttl))) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired operations", removed);
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Shuts down the thread pool gracefully.
     */
    public void shutdown() {
        log.info("Shutting down AsyncOperationManager ({} tracked operations)", operations.size());
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private TrackedOperation requireOperation(String operationId) {
        TrackedOperation op = operations.get(operationId);
        if (op == null) {
            throw new CommandExecutionException("Operation not found: " + operationId);
        }
        return op;
    }

    private void updateState(String operationId, ExecutionState state) {
        updateState(operationId, state, null);
    }

    private void updateState(String operationId, ExecutionState state, String errorMessage) {
        TrackedOperation op = operations.get(operationId);
        if (op == null) {
            return;
        }

        OperationStatus status = op.getStatus();
        status.setState(state);

        if (errorMessage != null && !errorMessage.isEmpty()) {
            status.setErrorMessage(errorMessage);
        }

        if (status.isComplete()) {
            status.setCompletedAt(LocalDateTime.now());
            status.setDurationMs(Duration.between(status.getStartedAt(), status.getCompletedAt()).toMillis());
        }
    }

    /**
     * Internal holder for a tracked asynchronous operation.
     */
    @Data
    @AllArgsConstructor
    static class TrackedOperation {
        private String operationId;
        private String containerId;
        private DatabaseCommand command;
        private Future<CommandResult> future;
        private OperationStatus status;
    }
}

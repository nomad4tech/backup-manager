package tech.nomad4.backupmanager.isolate.command.service;

import com.github.dockerjava.api.DockerClient;
import tech.nomad4.backupmanager.isolate.command.dto.CommandResult;
import tech.nomad4.backupmanager.isolate.command.dto.DatabaseCommand;
import tech.nomad4.backupmanager.isolate.command.dto.OperationStatus;
import tech.nomad4.backupmanager.isolate.command.exception.CommandExecutionException;

import java.util.List;

/**
 * Service for executing commands inside Docker database containers.
 * <p>
 * Accepts a {@link DockerClient} and a plain {@code containerId} string so that
 * this service remains fully decoupled from the discovery and socket modules.
 * Supports both synchronous and asynchronous execution with operation tracking.
 * </p>
 */
public interface DatabaseCommandService {

    /**
     * Executes a command synchronously, blocking until completion or timeout.
     *
     * @param client      the Docker client to use
     * @param containerId the target container ID (short or long form)
     * @param command     the command to execute
     * @return the command result with captured stdout/stderr
     * @throws CommandExecutionException if execution fails
     */
    CommandResult execute(DockerClient client, String containerId, DatabaseCommand command);

    /**
     * Submits a command for asynchronous execution and returns immediately.
     *
     * @param client      the Docker client to use
     * @param containerId the target container ID (short or long form)
     * @param command     the command to execute
     * @return the operation ID for status tracking
     * @throws CommandExecutionException if the command cannot be submitted
     */
    String executeAsync(DockerClient client, String containerId, DatabaseCommand command);

    /**
     * Returns the current status of an asynchronous operation.
     *
     * @param operationId the operation ID returned by {@link #executeAsync}
     * @return current operation status
     * @throws CommandExecutionException if the operation is not found
     */
    OperationStatus getOperationStatus(String operationId);

    /**
     * Returns the result of a completed asynchronous operation.
     *
     * @param operationId the operation ID
     * @param blocking    if {@code true}, blocks until the operation completes;
     *                    if {@code false}, returns {@code null} when not yet complete
     * @return the command result, or {@code null} if non-blocking and incomplete
     * @throws CommandExecutionException if the operation is not found or retrieval fails
     */
    CommandResult getResult(String operationId, boolean blocking);

    /**
     * Cancels a running or queued asynchronous operation.
     *
     * @param operationId the operation ID
     * @return {@code true} if the operation was cancelled, {@code false} if already complete
     */
    boolean cancel(String operationId);

    /**
     * Lists all tracked operations (running and recently completed).
     *
     * @return list of operation statuses
     */
    List<OperationStatus> listOperations();

    /**
     * Lists operations targeting a specific container.
     *
     * @param containerId the container ID to filter by
     * @return list of operation statuses for that container
     */
    List<OperationStatus> listOperations(String containerId);

    /**
     * Queries the actual on-disk size of the specified database from inside the container.
     *
     * @param client       the Docker client to use
     * @param containerId  the target container ID (short or long form)
     * @param databaseName the name of the database to measure
     * @return size in bytes; 0 if the database is empty or has no tables
     * @throws CommandExecutionException if the query fails or the output cannot be parsed
     */
    long getDatabaseSize(DockerClient client, String containerId, String databaseName);
}

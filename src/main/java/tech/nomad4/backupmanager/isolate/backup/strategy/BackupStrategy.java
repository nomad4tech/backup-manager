package tech.nomad4.backupmanager.isolate.backup.strategy;

import com.github.dockerjava.api.DockerClient;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupCommand;
import tech.nomad4.backupmanager.isolate.backup.exception.BackupException;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.backup.service.BackupExecutionService;

/**
 * Strategy for executing a database backup inside a Docker container.
 * <p>
 * Each database engine (PostgreSQL, MySQL, etc.) provides its own implementation.
 * The implementation is responsible only for streaming the dump to the output file
 * path specified in {@link BackupCommand#getOutputFilePath()}. Directory creation,
 * file verification, and checksum calculation are handled at a higher level.
 * </p>
 *
 * <h3>Registration</h3>
 * Annotate implementations with {@code @Component}. Spring will inject all
 * registered strategies into {@link BackupExecutionService}
 * via a {@code List<BackupStrategy>}, which selects the correct one by
 * {@link #getSupportedType()}.
 */
public interface BackupStrategy {

    /**
     * Returns the database type this strategy handles.
     *
     * @return supported database type
     */
    DatabaseType getSupportedType();

    /**
     * Executes the backup and writes the output to the file path given by
     * {@link BackupCommand#getOutputFilePath()}.
     * <p>
     * The caller guarantees the output directory already exists. Implementations
     * must not call any other service - they work only with the Docker client
     * and the file system.
     * </p>
     *
     * @param dockerClient authenticated Docker client for the target host
     * @param command       backup parameters (container ID, database name, output path, options)
     * @throws BackupException if the backup process fails or exits with a non-zero code
     */
    void execute(DockerClient dockerClient, BackupCommand command) throws BackupException;
}

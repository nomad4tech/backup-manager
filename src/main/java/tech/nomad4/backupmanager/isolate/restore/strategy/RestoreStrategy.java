package tech.nomad4.backupmanager.isolate.restore.strategy;

import com.github.dockerjava.api.DockerClient;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.restore.dto.RestoreCommand;
import tech.nomad4.backupmanager.isolate.restore.exception.RestoreException;
import tech.nomad4.backupmanager.isolate.restore.service.RestoreExecutionService;

/**
 * Strategy for restoring a database inside a Docker container.
 * <p>
 * Each database engine (PostgreSQL, MySQL, etc.) provides its own implementation.
 * The implementation is responsible only for streaming the backup file into the
 * target database. Directory management and record persistence are handled at a
 * higher level.
 * </p>
 *
 * <h3>Registration</h3>
 * Annotate implementations with {@code @Component}. Spring will inject all
 * registered strategies into {@link RestoreExecutionService}
 * via a {@code List<RestoreStrategy>}, which selects the correct one by
 * {@link #getSupportedType()}.
 */
public interface RestoreStrategy {

    /**
     * Returns the database type this strategy handles.
     *
     * @return supported database type
     */
    DatabaseType getSupportedType();

    /**
     * Executes the restore by streaming the file at
     * {@link RestoreCommand#getInputFilePath()} into the target database.
     *
     * @param dockerClient authenticated Docker client for the target host
     * @param command       restore parameters (container ID, database name, input path, options)
     * @throws RestoreException if the restore process fails or exits with a non-zero code
     */
    void execute(DockerClient dockerClient, RestoreCommand command) throws RestoreException;
}

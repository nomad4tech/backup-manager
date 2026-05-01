package tech.nomad4.backupmanager.isolate.restore.strategy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.restore.dto.RestoreCommand;
import tech.nomad4.backupmanager.isolate.restore.exception.RestoreException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * PostgreSQL restore strategy using {@code psql}.
 * <p>
 * First creates the target database via {@code createdb}, then streams the backup
 * file into {@code psql} via Docker exec stdin. Compressed backups are decompressed
 * inside the container via {@code gunzip | psql}. Standard error frames are logged
 * at WARN level.
 * </p>
 */
@Slf4j
public class PostgresRestoreStrategy implements RestoreStrategy {

    @Override
    public DatabaseType getSupportedType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public void execute(DockerClient dockerClient, RestoreCommand command) throws RestoreException {
        createDatabase(dockerClient, command);

        String[] restoreCmd = buildRestoreCommand(command);
        log.debug("Executing psql restore: {}", String.join(" ", restoreCmd));

        Path inputPath = Paths.get(command.getInputFilePath());

        ExecCreateCmdResponse exec = null;
        try {
            exec = dockerClient.execCreateCmd(command.getContainerId())
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(restoreCmd)
                    .exec();

            try (InputStream fileInput = Files.newInputStream(inputPath)) {
                dockerClient.execStartCmd(exec.getId())
                        .withDetach(false)
                        .withTty(false)
                        .withStdIn(fileInput)
                        .exec(new ExecStartResultCallback() {
                            @Override
                            public void onNext(Frame frame) {
                                if (frame.getStreamType() == StreamType.STDERR) {
                                    String line = new String(frame.getPayload(), StandardCharsets.UTF_8)
                                            .stripTrailing();
                                    if (!line.isBlank()) {
                                        log.warn("psql stderr: {}", line);
                                    }
                                }
                                super.onNext(frame);
                            }
                        })
                        .awaitCompletion(command.getTimeoutSeconds(), TimeUnit.SECONDS);
            }

            InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
            Long exitCode = inspect.getExitCodeLong();

            if (exitCode != null && exitCode != 0) {
                throw new RestoreException("psql exited with code: " + exitCode);
            }

        } catch (RestoreException e) {
            throw e;
        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            throw new RestoreException("Failed to connect to container: " + command.getContainerId(), e);
        } catch (IOException e) {
            throw new RestoreException("Failed to open input file: " + inputPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestoreException("Restore interrupted", e);
        } catch (Exception e) {
            // okhttp closes the connection after stdin EOF which causes
            // AsynchronousCloseException / SocketException — psql may have already
            // succeeded. Always attempt to inspect exit code before declaring failure.
            if (exec != null) {
                try {
                    InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
                    Long exitCode = inspect.getExitCodeLong();

                    if (exitCode == null) {
                        // exec state not yet settled — wait briefly and retry once
                        Thread.sleep(2000);
                        inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
                        exitCode = inspect.getExitCodeLong();
                    }

                    if (exitCode != null && exitCode == 0) {
                        log.warn("psql exited with code 0 despite connection error — treating as success. Error was: {}", e.getMessage());
                        return;
                    }

                    if (exitCode != null && exitCode != 0) {
                        throw new RestoreException("psql exited with code: " + exitCode);
                    }

                } catch (RestoreException re) {
                    throw re;
                } catch (Exception inspectEx) {
                    log.warn("Could not inspect exec after error: {}", inspectEx.getMessage());
                }
            }
            throw new RestoreException("Unexpected error during psql restore", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void createDatabase(DockerClient dockerClient, RestoreCommand command) throws RestoreException {
        String dbName = command.getDatabaseName();
        String[] createDbCmd = {"sh", "-c", "createdb -U \"$POSTGRES_USER\" " + shellQuote(dbName)};

        log.debug("Creating database '{}' via createdb", dbName);

        try {
            ExecCreateCmdResponse createDbExec = dockerClient.execCreateCmd(command.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(createDbCmd)
                    .exec();

            dockerClient.execStartCmd(createDbExec.getId())
                    .withDetach(false)
                    .withTty(false)
                    .exec(new ExecStartResultCallback())
                    .awaitCompletion(command.getTimeoutSeconds(), TimeUnit.SECONDS);

            InspectExecResponse inspect = dockerClient.inspectExecCmd(createDbExec.getId()).exec();
            Long exitCode = inspect.getExitCodeLong();

            if (exitCode != null && exitCode != 0) {
                throw new RestoreException(
                        "Database '" + dbName + "' already exists or could not be created");
            }

        } catch (RestoreException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestoreException("Restore interrupted during database creation", e);
        } catch (Exception e) {
            throw new RestoreException("Failed to create database '" + dbName + "'", e);
        }
    }

    private String[] buildRestoreCommand(RestoreCommand command) {
        String dbName = shellQuote(command.getDatabaseName());
        if (command.isCompressed()) {
            return new String[]{"bash", "-c", "set -o pipefail; gunzip | psql -U \"$POSTGRES_USER\" " + dbName};
        }
        return new String[]{"sh", "-c", "psql -U \"$POSTGRES_USER\" " + dbName};
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
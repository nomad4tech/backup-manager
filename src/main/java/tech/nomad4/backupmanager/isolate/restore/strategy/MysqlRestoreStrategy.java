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
 * MySQL restore strategy using the {@code mysql} client.
 * <p>
 * First creates the target database, then streams the backup file into {@code mysql}
 * via Docker exec stdin. Compressed backups are decompressed inside the container
 * via {@code gunzip | mysql}. Authenticates as {@code root} using the container's
 * {@code $MYSQL_ROOT_PASSWORD} environment variable. Standard error frames are
 * logged at WARN level.
 * </p>
 */
@Slf4j
public class MysqlRestoreStrategy implements RestoreStrategy {

    @Override
    public DatabaseType getSupportedType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public void execute(DockerClient dockerClient, RestoreCommand command) throws RestoreException {
        createDatabase(dockerClient, command);

        String[] restoreCmd = buildRestoreCommand(command);
        log.debug("Executing mysql restore: {}", String.join(" ", restoreCmd));

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
                                        log.warn("mysql stderr: {}", line);
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
                throw new RestoreException("mysql exited with code: " + exitCode);
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
            // AsynchronousCloseException or SocketException can occur when okhttp closes
            // the connection after stdin EOF — mysql may have already succeeded.
            // Attempt to check exit code before declaring failure.
            boolean isConnectionReset = e.getMessage() != null && (
                e.getCause() instanceof java.nio.channels.AsynchronousCloseException ||
                e.getCause() instanceof java.net.SocketException ||
                (e.getCause() != null && e.getCause().getCause() instanceof java.nio.channels.AsynchronousCloseException) ||
                (e.getCause() != null && e.getCause().getCause() instanceof java.net.SocketException)
            );
            if (isConnectionReset && exec != null) {
                try {
                    InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
                    Long exitCode = inspect.getExitCodeLong();
                    if (exitCode != null && exitCode == 0) {
                        log.warn("mysqldump connection closed abruptly but exit code is 0 — treating as success");
                        return;
                    }
                } catch (Exception inspectEx) {
                    log.warn("Could not inspect exec after connection reset: {}", inspectEx.getMessage());
                }
            }
            throw new RestoreException("Unexpected error during mysql restore", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void createDatabase(DockerClient dockerClient, RestoreCommand command) throws RestoreException {
        String dbName = command.getDatabaseName();
        String[] createDbCmd = {
                "sh", "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -u root -e \"CREATE DATABASE `" + dbName + "`\""
        };

        log.debug("Creating database '{}' via mysql", dbName);

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
        String dbName = command.getDatabaseName();
        if (command.isCompressed()) {
            return new String[]{
                    "bash", "-c",
                    "set -o pipefail; gunzip | MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -u root " + dbName
            };
        }
        return new String[]{
                "sh", "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -u root " + dbName
        };
    }
}

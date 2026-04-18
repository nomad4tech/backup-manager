package tech.nomad4.backupmanager.isolate.backup.strategy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupCommand;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupFormat;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupOptions;
import tech.nomad4.backupmanager.isolate.backup.dto.CompressionType;
import tech.nomad4.backupmanager.isolate.backup.exception.BackupException;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PostgreSQL backup strategy using {@code pg_dump}.
 * <p>
 * Executes {@code pg_dump} via the Docker exec API and streams its standard
 * output frame-by-frame to the output file, avoiding in-memory buffering of
 * large dumps. Standard error frames are logged at WARN level.
 * </p>
 */
@Slf4j
public class PostgresBackupStrategy implements BackupStrategy {

    @Override
    public DatabaseType getSupportedType() {
        return DatabaseType.POSTGRES;
    }

    /**
     * Runs {@code pg_dump} inside the container specified by
     * {@link BackupCommand#getContainerId()} and streams stdout to
     * {@link BackupCommand#getOutputFilePath()}.
     *
     * @param dockerClient authenticated Docker client for the target host
     * @param command       backup parameters
     * @throws BackupException if the exec cannot be created, writing fails,
     *                         or {@code pg_dump} exits with a non-zero code
     */
    @Override
    public void execute(DockerClient dockerClient, BackupCommand command) throws BackupException {
        String[] pgDumpCmd = buildPgDumpCommand(command);

        log.debug("Executing pg_dump: {}", String.join(" ", pgDumpCmd));

        Path outputPath = Paths.get(command.getOutputFilePath());
        AtomicReference<IOException> writeError = new AtomicReference<>();

        try {
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(command.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(pgDumpCmd)
                    .exec();

            try (OutputStream fileOutput = Files.newOutputStream(outputPath)) {

                dockerClient.execStartCmd(exec.getId())
                        .withDetach(false)
                        .withTty(false)
                        .exec(new ExecStartResultCallback() {
                            @Override
                            public void onNext(Frame frame) {
                                if (frame.getStreamType() == StreamType.STDOUT) {
                                    try {
                                        fileOutput.write(frame.getPayload());
                                    } catch (IOException e) {
                                        writeError.set(e);
                                    }
                                } else if (frame.getStreamType() == StreamType.STDERR) {
                                    String line = new String(frame.getPayload(), StandardCharsets.UTF_8)
                                            .stripTrailing();
                                    if (!line.isBlank()) {
                                        log.warn("pg_dump stderr: {}", line);
                                    }
                                }
                                super.onNext(frame);
                            }
                        })
                        .awaitCompletion(command.getOptions().getTimeoutSeconds(), TimeUnit.SECONDS);
            }

            if (writeError.get() != null) {
                throw new BackupException("Failed to write backup data to output file", writeError.get());
            }

            InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
            Long exitCode = inspect.getExitCodeLong();

            if (exitCode != null && exitCode != 0) {
                throw new BackupException("pg_dump exited with code: " + exitCode);
            }

        } catch (BackupException e) {
            throw e;
        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            throw new BackupException(
                    "Failed to connect container: " + command.getContainerId(), e);
        } catch (IOException e) {
            throw new BackupException("Failed to open output file: " + outputPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupException("Backup interrupted", e);
        } catch (Exception e) {
            throw new BackupException("Unexpected error during pg_dump execution", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the exec command as {@code ["sh", "-c", "pg_dump -U \"$POSTGRES_USER\" ..."]}
     * so that the container's {@code POSTGRES_USER} environment variable is expanded
     * at runtime rather than using a hardcoded role name.
     */
    private String[] buildPgDumpCommand(BackupCommand command) {
        StringBuilder cmd = new StringBuilder("pg_dump -U \"$POSTGRES_USER\"");

        BackupOptions opts = command.getOptions();

        if (opts.getFormat() != BackupFormat.PLAIN) {
            cmd.append(" -F ").append(shellQuote(opts.getFormat().getPgDumpFormat()));
        }

        if (opts.getFormat() == BackupFormat.CUSTOM
                && opts.getCompression() == CompressionType.GZIP) {
            cmd.append(" -Z 9");
        }

        if (opts.isVerbose()) {
            cmd.append(" -v");
        }

        if (opts.getSchemas() != null && !opts.getSchemas().isEmpty()) {
            for (String schema : opts.getSchemas()) {
                cmd.append(" -n ").append(shellQuote(schema));
            }
        }

        if (opts.getExcludeTables() != null && !opts.getExcludeTables().isEmpty()) {
            for (String table : opts.getExcludeTables()) {
                cmd.append(" -T ").append(shellQuote(table));
            }
        }

        cmd.append(' ').append(shellQuote(command.getDatabaseName()));

        return new String[]{"sh", "-c", cmd.toString()};
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}

package tech.nomad4.backupmanager.isolate.backup.strategy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupCommand;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupOptions;
import tech.nomad4.backupmanager.isolate.backup.dto.CompressionType;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.backup.exception.BackupException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MySQL backup strategy using {@code mysqldump}.
 * <p>
 * Executes {@code mysqldump} via the Docker exec API and streams its standard
 * output frame-by-frame to the output file, avoiding in-memory buffering of
 * large dumps. Standard error frames are logged at WARN level.
 * </p>
 *
 * <h3>Authentication</h3>
 * Connects as {@code root} using the container's {@code $MYSQL_ROOT_PASSWORD}
 * environment variable, passed via {@code MYSQL_PWD} to avoid the CLI password
 * warning and to preserve the correct exit code.
 *
 * <h3>Compression</h3>
 * When {@link CompressionType#GZIP} is requested, the dump is piped through
 * {@code gzip} inside the container ({@code mysqldump ... | gzip}).
 * {@code bash} with {@code set -o pipefail} is used as the shell so that a
 * {@code mysqldump} failure propagates correctly through the pipe. Official
 * {@code mysql:*} images ship with {@code bash}.
 * For {@link CompressionType#NONE}, plain {@code sh} suffices.
 *
 * <h3>BackupOptions mapping</h3>
 * <ul>
 *   <li>{@code compression} → NONE: plain SQL; GZIP: piped through gzip</li>
 *   <li>{@code verbose}     → {@code --verbose}</li>
 *   <li>{@code excludeTables} → {@code --ignore-table=db.table};
 *       entries without a {@code .} are automatically prefixed with the database name</li>
 *   <li>{@code format} / {@code schemas} → ignored (not applicable to {@code mysqldump})</li>
 * </ul>
 *
 * <h3>MariaDB</h3>
 * MariaDB images expose {@code mysqldump} as an alias for {@code mariadb-dump}
 * and honour {@code $MYSQL_ROOT_PASSWORD}, so this strategy works for both engines.
 * A dedicated {@code MariadbBackupStrategy} can be added as a thin subclass if
 * engine-specific flags are needed in the future.
 */
@Slf4j
public class MysqlBackupStrategy implements BackupStrategy {

    @Override
    public DatabaseType getSupportedType() {
        return DatabaseType.MYSQL;
    }

    /**
     * Runs {@code mysqldump} inside the container specified by
     * {@link BackupCommand#getContainerId()} and streams stdout to
     * {@link BackupCommand#getOutputFilePath()}.
     *
     * @param dockerClient authenticated Docker client for the target host
     * @param command       backup parameters
     * @throws BackupException if the exec cannot be created, writing fails,
     *                         or {@code mysqldump} exits with a non-zero code
     */
    @Override
    public void execute(DockerClient dockerClient, BackupCommand command) throws BackupException {
        String[] dumpCmd = buildMysqldumpCommand(command);

        log.debug("Executing mysqldump: {}", String.join(" ", dumpCmd));

        Path outputPath = Paths.get(command.getOutputFilePath());
        AtomicReference<IOException> writeError = new AtomicReference<>();

        try {
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(command.getContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(dumpCmd)
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
                                        log.warn("mysqldump stderr: {}", line);
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
                throw new BackupException("mysqldump exited with code: " + exitCode);
            }

        } catch (BackupException e) {
            throw e;
        } catch (IOException e) {
            throw new BackupException("Failed to open output file: " + outputPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupException("Backup interrupted", e);
        } catch (Exception e) {
            throw new BackupException("Unexpected error during mysqldump execution", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the exec command for {@code mysqldump}.
     * <p>
     * Without GZIP: {@code ["sh", "-c", "MYSQL_PWD=... mysqldump -u root ..."]}
     * <br>
     * With GZIP: {@code ["bash", "-c", "set -o pipefail; MYSQL_PWD=... mysqldump ... | gzip"]}
     * - {@code bash} is required for {@code pipefail}, which propagates a
     * {@code mysqldump} failure through the pipe correctly.
     * </p>
     */
    private String[] buildMysqldumpCommand(BackupCommand command) {
        BackupOptions opts = command.getOptions();

        StringBuilder dump = new StringBuilder();
        dump.append("MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysqldump -u root");
        dump.append(" --single-transaction");
        dump.append(" --routines --triggers --events");

        if (opts.isVerbose()) {
            dump.append(" --verbose");
        }

        if (opts.getExcludeTables() != null && !opts.getExcludeTables().isEmpty()) {
            for (String table : opts.getExcludeTables()) {
                // mysqldump requires the fully-qualified form: --ignore-table=db.table
                String qualified = table.contains(".") ? table : command.getDatabaseName() + "." + table;
                dump.append(" --ignore-table=").append(shellQuote(qualified));
            }
        }

        dump.append(" --databases ").append(shellQuote(command.getDatabaseName()));

        if (opts.getCompression() == CompressionType.GZIP) {
            String script = "set -o pipefail; " + dump + " | gzip";
            return new String[]{"bash", "-c", script};
        }

        return new String[]{"sh", "-c", dump.toString()};
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}

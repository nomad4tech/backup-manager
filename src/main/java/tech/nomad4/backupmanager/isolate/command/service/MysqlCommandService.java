package tech.nomad4.backupmanager.isolate.command.service;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.command.dto.CommandResult;
import tech.nomad4.backupmanager.isolate.command.dto.DatabaseCommand;
import tech.nomad4.backupmanager.isolate.command.dto.OperationStatus;
import tech.nomad4.backupmanager.isolate.command.exception.CommandExecutionException;
import tech.nomad4.backupmanager.isolate.command.executor.AsyncOperationManager;
import tech.nomad4.backupmanager.isolate.command.executor.DockerExecExecutor;

import java.util.List;
import java.util.UUID;

/**
 * MySQL-specific implementation of {@link DatabaseCommandService}.
 * <p>
 * Translates {@link tech.nomad4.backupmanager.isolate.command.dto.CommandType#SQL}
 * commands into {@code mysql} CLI invocations and delegates execution to
 * {@link DockerExecExecutor}. Asynchronous operations are managed through
 * {@link AsyncOperationManager}.
 * </p>
 *
 * <h3>Authentication</h3>
 * SQL commands run as {@code root} with the password sourced from the container's
 * {@code $MYSQL_ROOT_PASSWORD} environment variable - always present in official
 * {@code mysql:*} and {@code mariadb:*} images. The password is forwarded via
 * {@code MYSQL_PWD} (not {@code -p}) to avoid the CLI password warning and to
 * preserve the correct exit code. The command is wrapped in {@code sh -c} so
 * variables are resolved by the container's shell at runtime, not by the JVM.
 *
 * <h3>SQL args convention</h3>
 * <ul>
 *   <li>{@code args[0]} - the SQL query text</li>
 *   <li>{@code args[1..n]} - optional extra {@code mysql} flags (e.g., {@code "-D"}, {@code "mydb"})</li>
 * </ul>
 *
 * <h3>MariaDB compatibility</h3>
 * MariaDB images accept {@code $MYSQL_ROOT_PASSWORD} alongside their own
 * {@code $MARIADB_ROOT_PASSWORD}, so this service works for both engines.
 */
@Slf4j
public class MysqlCommandService implements DatabaseCommandService {

    private final DockerExecExecutor executor;
    private final AsyncOperationManager operationManager;
    private final long defaultTimeout;

    public MysqlCommandService(DockerExecExecutor executor, AsyncOperationManager operationManager, long defaultTimeout) {
        this.executor = executor;
        this.operationManager = operationManager;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public CommandResult execute(DockerClient client, String containerId, DatabaseCommand command) {
        log.info("Executing command in container {}: {}", containerId, command.getSummary());

        String[] execCommand = buildExecCommand(command);
        long timeout = resolveTimeout(command);

        try {
            CommandResult result = executor.execute(client, containerId, execCommand, timeout, command.getEnv());
            result.setOperationId(UUID.randomUUID().toString());

            log.info("Command completed in {}ms with exit code {} in container {}",
                    result.getExecutionTimeMs(), result.getExitCode(), containerId);

            return result;

        } catch (Exception e) {
            log.error("Command execution failed in container {}: {}", containerId, e.getMessage());
            throw new CommandExecutionException("Failed to execute command", e);
        }
    }

    @Override
    public String executeAsync(DockerClient client, String containerId, DatabaseCommand command) {
        log.info("Submitting async command for container {}: {}", containerId, command.getSummary());

        String[] execCommand = buildExecCommand(command);
        long timeout = resolveTimeout(command);

        return operationManager.submit(
                containerId,
                command,
                () -> executor.execute(client, containerId, execCommand, timeout, command.getEnv())
        );
    }

    @Override
    public OperationStatus getOperationStatus(String operationId) {
        return operationManager.getStatus(operationId);
    }

    @Override
    public CommandResult getResult(String operationId, boolean blocking) {
        return operationManager.getResult(operationId, blocking);
    }

    @Override
    public boolean cancel(String operationId) {
        return operationManager.cancel(operationId);
    }

    @Override
    public List<OperationStatus> listOperations() {
        return operationManager.listAll();
    }

    @Override
    public List<OperationStatus> listOperations(String containerId) {
        return operationManager.listByContainer(containerId);
    }

    @Override
    public long getDatabaseSize(DockerClient client, String containerId, String databaseName) {
        String sql = "SELECT SUM(data_length + index_length)" +
                     " FROM information_schema.tables" +
                     " WHERE table_schema = '" + databaseName.replace("'", "''") + "'";
        DatabaseCommand command = DatabaseCommand.sql(sql, "-BN").withTimeout(10L);
        CommandResult result = execute(client, containerId, command);
        if (!result.isSuccess()) {
            throw new CommandExecutionException("Failed to query MySQL database size: " + result.getError());
        }
        return parseSingleLong(result.getStdout(), containerId);
    }

    private long parseSingleLong(String stdout, String containerId) {
        if (stdout != null) {
            for (String line : stdout.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    if ("NULL".equalsIgnoreCase(trimmed)) {
                        return 0L;  // schema exists but has no tables yet
                    }
                    try {
                        return Long.parseLong(trimmed);
                    } catch (NumberFormatException e) {
                        throw new CommandExecutionException(
                                "Unexpected database size output in container " + containerId + ": " + trimmed);
                    }
                }
            }
        }
        throw new CommandExecutionException(
                "No parseable output when querying database size in container " + containerId);
    }

    // ------------------------------------------------------------------
    // Command building
    // ------------------------------------------------------------------

    private String[] buildExecCommand(DatabaseCommand command) {
        return switch (command.getType()) {
            case SQL   -> buildSqlCommand(command);
            case SHELL -> command.getArgs().toArray(new String[0]);
            case RAW   -> command.getArgs().toArray(new String[0]);
        };
    }

    /**
     * Wraps a SQL query in a {@code mysql} CLI invocation.
     * <p>
     * Runs as {@code root} with the password sourced from {@code $MYSQL_ROOT_PASSWORD}.
     * The password is passed via the {@code MYSQL_PWD} environment variable rather
     * than the {@code -p} flag, which avoids the
     * {@code "Warning: Using a password on the command line interface can be insecure"}
     * message and preserves the correct exit code (piping through {@code grep} to strip
     * the warning would swallow the mysql exit code on failure).
     * </p>
     * <p>
     * The command is executed via {@code sh -c} so {@code $MYSQL_ROOT_PASSWORD} is
     * resolved by the container's shell at runtime, not by the JVM.
     * </p>
     */
    private String[] buildSqlCommand(DatabaseCommand command) {
        List<String> args = command.getArgs();
        String sql = args.get(0);

        StringBuilder mysqlCmd = new StringBuilder();
        mysqlCmd.append("MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -u root");

        for (int i = 1; i < args.size(); i++) {
            mysqlCmd.append(' ').append(shellQuote(args.get(i)));
        }

        mysqlCmd.append(" -e ").append(shellQuote(sql));

        return new String[]{"sh", "-c", mysqlCmd.toString()};
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private long resolveTimeout(DatabaseCommand command) {
        return command.getTimeoutSeconds() != null ? command.getTimeoutSeconds() : defaultTimeout;
    }
}

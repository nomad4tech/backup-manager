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
 * {@code $MARIADB_ROOT_PASSWORD}. Some images ship only the {@code mariadb} CLI
 * and not {@code mysql}. This service tries {@code mysql} first and falls back to
 * {@code mariadb} automatically if the CLI is not found.
 */
@Slf4j
public class MysqlCommandService implements DatabaseCommandService {

    private static final String[] CLI_CANDIDATES = {"mysql", "mariadb"};

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
        long timeout = resolveTimeout(command);

        for (String cli : CLI_CANDIDATES) {
            String[] execCommand = buildExecCommandWithCli(command, cli);
            try {
                CommandResult result = executor.execute(client, containerId, execCommand, timeout, command.getEnv());
                if (isMysqlNotFound(result) && "mysql".equals(cli)) {
                    log.info("mysql not found in container {}, retrying with mariadb CLI", containerId);
                    continue;
                }
                result.setOperationId(UUID.randomUUID().toString());
                log.info("Command completed in {}ms with exit code {} in container {}",
                        result.getExecutionTimeMs(), result.getExitCode(), containerId);
                return result;
            } catch (Exception e) {
                if ("mariadb".equals(cli)) {
                    log.error("Command execution failed in container {}: {}", containerId, e.getMessage());
                    throw new CommandExecutionException("Failed to execute command", e);
                }
                log.warn("mysql CLI failed in container {}, trying mariadb", containerId);
            }
        }
        throw new CommandExecutionException(
                "Neither mysql nor mariadb CLI found in container " + containerId);
    }

    @Override
    public String executeAsync(DockerClient client, String containerId, DatabaseCommand command) {
        log.info("Submitting async command for container {}: {}", containerId, command.getSummary());
        long timeout = resolveTimeout(command);

        return operationManager.submit(
                containerId,
                command,
                () -> {
                    for (String cli : CLI_CANDIDATES) {
                        String[] execCommand = buildExecCommandWithCli(command, cli);
                        CommandResult result = executor.execute(
                                client, containerId, execCommand, timeout, command.getEnv());
                        if (isMysqlNotFound(result) && "mysql".equals(cli)) continue;
                        return result;
                    }
                    throw new CommandExecutionException(
                            "Neither mysql nor mariadb CLI found in container " + containerId);
                }
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
                        return 0L;
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

    private String[] buildExecCommandWithCli(DatabaseCommand command, String cli) {
        return switch (command.getType()) {
            case SQL   -> buildSqlCommand(command, cli);
            case SHELL -> command.getArgs().toArray(new String[0]);
            case RAW   -> command.getArgs().toArray(new String[0]);
        };
    }

    private String[] buildSqlCommand(DatabaseCommand command, String cli) {
        List<String> args = command.getArgs();
        String sql = args.get(0);

        StringBuilder mysqlCmd = new StringBuilder();
        mysqlCmd.append("MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" ").append(cli).append(" -u root");

        for (int i = 1; i < args.size(); i++) {
            mysqlCmd.append(' ').append(shellQuote(args.get(i)));
        }

        mysqlCmd.append(" -e ").append(shellQuote(sql));

        return new String[]{"sh", "-c", mysqlCmd.toString()};
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isMysqlNotFound(CommandResult result) {
        if (result == null) return false;
        String stderr = result.getError();
        if (stderr == null) return false;
        return stderr.contains("mysql: not found")
                || stderr.contains("mysql: command not found")
                || stderr.contains("No such file or directory");
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private long resolveTimeout(DatabaseCommand command) {
        return command.getTimeoutSeconds() != null ? command.getTimeoutSeconds() : defaultTimeout;
    }
}

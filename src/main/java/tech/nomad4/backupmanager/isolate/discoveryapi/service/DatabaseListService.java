package tech.nomad4.backupmanager.isolate.discoveryapi.service;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.isolate.command.dto.CommandResult;
import tech.nomad4.backupmanager.isolate.command.dto.DatabaseCommand;
import tech.nomad4.backupmanager.isolate.command.exception.CommandExecutionException;
import tech.nomad4.backupmanager.isolate.command.service.MysqlCommandService;
import tech.nomad4.backupmanager.isolate.command.service.PostgresCommandService;
import tech.nomad4.backupmanager.discovery.entity.DatabaseType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Retrieves the list of databases that exist inside a running container.
 * <p>
 * Delegates execution to the engine-specific {@link PostgresCommandService} or
 * {@link MysqlCommandService} and parses the plain-text output into a clean list
 * of names. The two services are injected by concrete type so Spring can
 * distinguish them without qualifiers.
 * </p>
 * <p>
 * Supported engines: {@link DatabaseType#POSTGRES}, {@link DatabaseType#MYSQL},
 * {@link DatabaseType#MARIADB} (MariaDB uses the same MySQL CLI and system tables).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseListService {

    private final PostgresCommandService postgresCommandService;
    private final MysqlCommandService mysqlCommandService;

    /**
     * Lists all user-visible databases in the given container.
     *
     * @param client      Docker client for the target host
     * @param containerId short or full container ID
     * @param dbType      engine type - determines which query is executed
     * @return sorted list of database names
     * @throws IllegalArgumentException  if {@code dbType} does not support database listing
     * @throws CommandExecutionException if the in-container query fails
     */
    public List<String> listDatabases(DockerClient client, String containerId, DatabaseType dbType) {
        log.debug("Listing databases in container {} (type={})", containerId, dbType);
        return switch (dbType) {
            case POSTGRES        -> listPostgresDatabases(client, containerId);
            case MYSQL, MARIADB  -> listMysqlDatabases(client, containerId, dbType);
            default              -> throw new IllegalArgumentException(
                    "Database listing is not supported for type: " + dbType.getDisplayName());
        };
    }

    // -------------------------------------------------------------------------
    // Engine-specific implementations
    // -------------------------------------------------------------------------

    /**
     * Runs {@code SELECT datname FROM pg_database WHERE datistemplate = false}
     * with {@code psql -At} (unaligned, tuples-only) so each line of stdout is
     * exactly one database name with no formatting noise.
     */
    private List<String> listPostgresDatabases(DockerClient client, String containerId) {
        DatabaseCommand command = DatabaseCommand.sql(
                "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;",
                "-At",            // -A: no alignment padding, -t: tuples only (no header/footer)
                "-d", "postgres"  // always connect to the system database, not the username default
        ).withTimeout(10L);

        CommandResult result = postgresCommandService.execute(client, containerId, command);

        if (!result.isSuccess()) {
            throw new CommandExecutionException(
                    "Failed to list PostgreSQL databases in container " + containerId +
                    ": " + result.getError());
        }

        return parseLines(result.getStdout());
    }

    /**
     * Queries {@code information_schema.schemata} to list user-created databases,
     * excluding MySQL's built-in system schemas ({@code information_schema},
     * {@code mysql}, {@code performance_schema}, {@code sys}).
     * <p>
     * Uses {@code -BN} flags ({@code --batch --skip-column-names}) for clean
     * one-name-per-line output with no formatting noise.
     * Works for both MySQL and MariaDB - MariaDB exposes the same
     * {@code information_schema} and recognises the same CLI flags.
     * </p>
     */
    private List<String> listMysqlDatabases(DockerClient client, String containerId, DatabaseType dbType) {
        DatabaseCommand command = DatabaseCommand.sql(
                "SELECT schema_name FROM information_schema.schemata" +
                " WHERE schema_name NOT IN ('information_schema','mysql','performance_schema','sys')" +
                " ORDER BY schema_name;",
                "-BN"   // --batch: tab-separated output; -N: suppress column header
        ).withTimeout(10L);

        CommandResult result = mysqlCommandService.execute(client, containerId, command);

        if (!result.isSuccess()) {
            throw new CommandExecutionException(
                    "Failed to list " + dbType.getDisplayName() + " databases in container " +
                    containerId + ": " + result.getError());
        }

        return parseLines(result.getStdout());
    }

    // -------------------------------------------------------------------------
    // Output parsing
    // -------------------------------------------------------------------------

    private List<String> parseLines(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stdout.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }
}

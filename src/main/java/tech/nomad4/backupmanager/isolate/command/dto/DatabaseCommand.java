package tech.nomad4.backupmanager.isolate.command.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Describes a command to execute inside a database container.
 * <p>
 * Use the static factory methods ({@link #sql}, {@link #shell}, {@link #raw})
 * for convenient construction, or the builder for full control. Timeouts and
 * TTL values fall back to application defaults when left {@code null}.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseCommand {

    private CommandType type;
    private List<String> args;

    /** Command timeout in seconds. {@code null} means use the application default. */
    private Long timeoutSeconds;

    /** How long to keep the result after completion, in seconds. {@code null} means use the application default. */
    private Long resultTtlSeconds;

    /** Optional environment variables to set before execution. */
    private Map<String, String> env;

    // ------------------------------------------------------------------
    // Factory methods
    // ------------------------------------------------------------------

    /**
     * Creates a SQL command with default timeout and TTL.
     * <p>
     * Additional psql flags can be provided after the query string
     * (e.g., {@code sql("SELECT 1", "-d", "mydb")}).
     * </p>
     *
     * @param query     the SQL query string
     * @param psqlFlags optional extra psql flags (e.g., "-d", "mydb")
     * @return a new {@link DatabaseCommand}
     */
    public static DatabaseCommand sql(String query, String... psqlFlags) {
        List<String> argsList = new ArrayList<>();
        argsList.add(query);
        argsList.addAll(Arrays.asList(psqlFlags));
        return DatabaseCommand.builder()
                .type(CommandType.SQL)
                .args(argsList)
                .env(Map.of())
                .build();
    }

    /**
     * Creates a shell command with default timeout and TTL.
     *
     * @param args the command and its arguments
     * @return a new {@link DatabaseCommand}
     */
    public static DatabaseCommand shell(String... args) {
        return DatabaseCommand.builder()
                .type(CommandType.SHELL)
                .args(Arrays.asList(args))
                .env(Map.of())
                .build();
    }

    /**
     * Creates a shell command with a custom timeout.
     *
     * @param timeoutSeconds timeout in seconds
     * @param args           the command and its arguments
     * @return a new {@link DatabaseCommand}
     */
    public static DatabaseCommand shell(Long timeoutSeconds, String... args) {
        return DatabaseCommand.builder()
                .type(CommandType.SHELL)
                .args(Arrays.asList(args))
                .timeoutSeconds(timeoutSeconds)
                .env(Map.of())
                .build();
    }

    /**
     * Creates a raw command passed directly to the container.
     *
     * @param args the command and its arguments
     * @return a new {@link DatabaseCommand}
     */
    public static DatabaseCommand raw(String... args) {
        return DatabaseCommand.builder()
                .type(CommandType.RAW)
                .args(Arrays.asList(args))
                .env(Map.of())
                .build();
    }

    // ------------------------------------------------------------------
    // Modifiers (return a copy)
    // ------------------------------------------------------------------

    /**
     * Returns a copy of this command with a different timeout.
     *
     * @param seconds timeout in seconds
     * @return a new {@link DatabaseCommand}
     */
    public DatabaseCommand withTimeout(Long seconds) {
        return DatabaseCommand.builder()
                .type(type).args(args)
                .timeoutSeconds(seconds)
                .resultTtlSeconds(resultTtlSeconds)
                .env(env)
                .build();
    }

    /**
     * Returns a copy of this command with a different result TTL.
     *
     * @param seconds TTL in seconds
     * @return a new {@link DatabaseCommand}
     */
    public DatabaseCommand withTtl(Long seconds) {
        return DatabaseCommand.builder()
                .type(type).args(args)
                .timeoutSeconds(timeoutSeconds)
                .resultTtlSeconds(seconds)
                .env(env)
                .build();
    }

    /**
     * Returns a copy of this command with additional environment variables.
     *
     * @param environment the environment variables to set
     * @return a new {@link DatabaseCommand}
     */
    public DatabaseCommand withEnv(Map<String, String> environment) {
        return DatabaseCommand.builder()
                .type(type).args(args)
                .timeoutSeconds(timeoutSeconds)
                .resultTtlSeconds(resultTtlSeconds)
                .env(environment)
                .build();
    }

    /**
     * Returns a short summary suitable for log messages.
     *
     * @return truncated command string (max 50 characters)
     */
    public String getSummary() {
        String cmd = String.join(" ", args);
        return cmd.length() > 50 ? cmd.substring(0, 47) + "..." : cmd;
    }
}

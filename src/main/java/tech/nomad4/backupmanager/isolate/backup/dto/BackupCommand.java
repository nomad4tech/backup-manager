package tech.nomad4.backupmanager.isolate.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.nomad4.backupmanager.isolate.backup.strategy.BackupStrategy;

/**
 * Descriptor for a single database backup operation.
 * <p>
 * Use {@link #create(String, String, String, DatabaseType)} for the common case;
 * the builder is available for advanced configuration.
 * </p>
 * <p>
 * {@code BackupCommand} carries only the data needed to execute the dump.
 * Directory creation and post-backup verification are the caller's responsibility.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupCommand {

    /** Short container ID of the target Docker container. */
    private String containerId;

    /** Name of the database to back up. */
    private String databaseName;

    /**
     * Absolute path (on the host) where the backup file will be written.
     * The parent directory must already exist before the command is executed.
     */
    private String outputFilePath;

    /** Database type - used to select the correct {@link BackupStrategy}. */
    private DatabaseType databaseType;

    /** Dump format and compression options. */
    private BackupOptions options;

    /**
     * Database superuser for authentication.
     * Defaults to {@code "postgres"}; used by the PostgreSQL strategy.
     */
    private String postgresUser;

    /**
     * Creates a command with default options and the {@code postgres} user.
     *
     * @param containerId    Docker container ID
     * @param databaseName   database to dump
     * @param outputFilePath destination file path (parent directory must exist)
     * @param databaseType   database engine - determines which strategy executes the backup
     * @param timeoutSeconds maximum seconds to wait for the dump to complete
     * @return configured backup command
     */
    public static BackupCommand create(
            String containerId,
            String databaseName,
            String outputFilePath,
            DatabaseType databaseType,
            long timeoutSeconds
    ) {
        BackupOptions options = BackupOptions.defaults();
        options.setTimeoutSeconds(timeoutSeconds);
        return BackupCommand.builder()
                .containerId(containerId)
                .databaseName(databaseName)
                .outputFilePath(outputFilePath)
                .databaseType(databaseType)
                .options(options)
                .postgresUser("postgres")
                .build();
    }
}

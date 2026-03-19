package tech.nomad4.backupmanager.isolate.backup.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tech.nomad4.backupmanager.isolate.backup.exception.BackupException;
import tech.nomad4.backupmanager.isolate.backup.service.BackupExecutionService;
import tech.nomad4.backupmanager.isolate.backup.strategy.BackupStrategy;

/**
 * Database types recognised by the backup module.
 * <p>
 * This enum is intentionally independent of
 * {@link tech.nomad4.backupmanager.isolate.discovery.entity.DatabaseType}. The two enums
 * serve different bounded contexts:
 * <ul>
 *   <li><b>discovery</b> – detects <em>any</em> database container from a Docker image name.</li>
 *   <li><b>backup</b>    – knows how to <em>dump</em> a specific database engine.</li>
 * </ul>
 * Mapping between them happens at the boundary (orchestrator / dev runner) and is
 * the only place that imports both types. This ensures the backup module can be
 * extracted into a standalone service without pulling in discovery code.
 * </p>
 *
 * <h3>Adding a new engine</h3>
 * <ol>
 *   <li>Add a value here.</li>
 *   <li>Implement {@link BackupStrategy}
 *       and annotate it with {@code @Component}.</li>
 *   <li>Update the mapping in the orchestrator / dev runner.</li>
 * </ol>
 */
@Getter
@RequiredArgsConstructor
public enum DatabaseType {

    POSTGRES("PostgreSQL"),
    MYSQL("MySQL"),
    MARIADB("MariaDB"),
    MONGODB("MongoDB"),
    REDIS("Redis"),

    /**
     * Catch-all for types that discovery knows about but the backup module
     * has no strategy for. {@link BackupExecutionService}
     * will throw {@link BackupException}
     * if a command is submitted with this type.
     */
    UNSUPPORTED("Unsupported");

    private final String displayName;
}

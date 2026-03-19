package tech.nomad4.backupmanager.scheduler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application-level backup configuration.
 *
 * <pre>
 * backup-manager:
 *   backup:
 *     base-directory: /backups
 *     max-concurrent: 3
 *     min-free-space-bytes: 536870912   # 512 MB
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "backup-manager.backup")
public class BackupProperties {

    /**
     * Base directory for all backup files.
     * Task backups are stored under {@code {baseDirectory}/{taskName}/}.
     * Should be mounted as a Docker volume.
     * <p>
     * Can be overridden via environment variable
     * {@code BACKUP_MANAGER_BACKUP_BASE_DIRECTORY}.
     * </p>
     */
    private String baseDirectory = "./backups";

    /**
     * Maximum number of concurrent backup runs across all tasks.
     * Additional triggers are skipped (logged as a warning) until a slot is free.
     */
    private int maxConcurrent = 3;

    /**
     * Minimum free space (in bytes) that must remain after estimating the
     * backup size. Acts as a safety buffer to avoid filling the disk.
     * Default: 512 MB.
     */
    private long minFreeSpaceBytes = 536_870_912L;

    /**
     * Maximum time in seconds to wait for a dump command to complete.
     * A hung dump will be interrupted after this duration, freeing its semaphore slot.
     * Default: 3600 (1 hour).
     */
    private int defaultTimeout = 3600;
}

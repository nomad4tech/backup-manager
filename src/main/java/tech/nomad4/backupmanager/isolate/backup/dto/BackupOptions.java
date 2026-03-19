package tech.nomad4.backupmanager.isolate.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tuning options forwarded to {@code pg_dump}.
 * <p>
 * Use {@link #defaults()} for a plain-SQL dump with no compression.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupOptions {

    private BackupFormat format;
    private CompressionType compression;
    private boolean verbose;

    /** If non-empty, only these schemas are included in the dump ({@code -n}). */
    private List<String> schemas;

    /** Tables to exclude from the dump ({@code -T}). */
    private List<String> excludeTables;

    /** Maximum seconds to wait for the dump process to finish. Default: 3600 (1 hour). */
    private long timeoutSeconds;

    /**
     * Returns a sensible default configuration: plain SQL, no compression,
     * non-verbose, all schemas included, 1-hour timeout.
     *
     * @return default backup options
     */
    public static BackupOptions defaults() {
        return BackupOptions.builder()
                .format(BackupFormat.PLAIN)
                .compression(CompressionType.NONE)
                .verbose(false)
                .timeoutSeconds(3600)
                .build();
    }
}

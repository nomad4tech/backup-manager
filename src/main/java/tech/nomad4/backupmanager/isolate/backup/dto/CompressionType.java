package tech.nomad4.backupmanager.isolate.backup.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Compression applied to the backup output stream.
 * <p>
 * {@link #GZIP} compression is only meaningful when combined with
 * {@link BackupFormat#CUSTOM} (passed as {@code -Z 9} to {@code pg_dump}).
 * For {@link BackupFormat#PLAIN} output, post-processing (e.g. piping through
 * {@code gzip}) would be required instead.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum CompressionType {

    /** No compression; output is written as-is. */
    NONE("none", ""),

    /** Gzip compression at maximum level. */
    GZIP("gzip", ".gz");

    private final String name;
    private final String extension;
}

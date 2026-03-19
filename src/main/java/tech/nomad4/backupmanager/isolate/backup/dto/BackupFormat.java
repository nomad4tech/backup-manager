package tech.nomad4.backupmanager.isolate.backup.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Output format options for {@code pg_dump}.
 * <p>
 * The {@link #pgDumpFormat} value is passed directly to {@code pg_dump -F}.
 * {@link #extension} is appended to the output file name when constructing
 * backup file paths.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum BackupFormat {

    /** Plain SQL text; the default and most portable format. */
    PLAIN("plain", ".sql"),

    /**
     * Custom PostgreSQL binary format; supports selective restore and
     * compression at the dump level.
     */
    CUSTOM("custom", ".dump"),

    /** Tar archive containing plain-format dump files. */
    TAR("tar", ".tar"),

    /**
     * Directory format; each table is a separate file.
     * Requires an output directory rather than a single file.
     */
    DIRECTORY("directory", "");

    private final String pgDumpFormat;
    private final String extension;
}

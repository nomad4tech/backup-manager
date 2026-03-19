package tech.nomad4.backupmanager.isolate.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.nomad4.backupmanager.isolate.storage.service.StorageService;

import java.time.LocalDateTime;

/**
 * Metadata about a file or directory on the local file system.
 * <p>
 * The {@code checksum} field is not populated by default; call
 * {@link StorageService#calculateChecksum}
 * separately and set it when verification is needed.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    private String path;
    private String name;
    private long sizeBytes;
    private LocalDateTime created;
    private LocalDateTime modified;

    /** Hex checksum string; {@code null} unless explicitly computed. */
    private String checksum;

    private boolean isDirectory;

    /**
     * Returns the file size formatted as a human-readable string (KB, MB, or GB).
     *
     * @return formatted size string, e.g. {@code "2.5 MB"}
     */
    public String getSizeFormatted() {
        if (sizeBytes >= 1_073_741_824L) {
            return String.format("%.1f GB", sizeBytes / 1_073_741_824.0);
        } else if (sizeBytes >= 1_048_576L) {
            return String.format("%.1f MB", sizeBytes / 1_048_576.0);
        } else if (sizeBytes >= 1_024L) {
            return String.format("%.1f KB", sizeBytes / 1_024.0);
        } else {
            return sizeBytes + " B";
        }
    }
}

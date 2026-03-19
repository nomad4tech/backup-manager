package tech.nomad4.backupmanager.isolate.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Disk space statistics for a file-system partition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskSpaceInfo {

    private String path;
    private long totalBytes;
    private long usedBytes;
    private long freeBytes;
    private int usagePercent;

    /**
     * Returns the total partition size formatted as gigabytes.
     *
     * @return formatted string, e.g. {@code "100.0 GB"}
     */
    public String getTotalFormatted() {
        return String.format("%.1f GB", totalBytes / 1_073_741_824.0);
    }

    /**
     * Returns the available (usable) space formatted as gigabytes.
     *
     * @return formatted string, e.g. {@code "45.2 GB"}
     */
    public String getFreeFormatted() {
        return String.format("%.1f GB", freeBytes / 1_073_741_824.0);
    }
}

package tech.nomad4.backupmanager.isolate.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Policy that controls how old files are selected for deletion during cleanup.
 * <p>
 * Three retention strategies are supported:
 * <ul>
 *   <li>{@link RetentionType#COUNT} – keep only the most recent N files.</li>
 *   <li>{@link RetentionType#TIME}  – keep files modified within the last N days.</li>
 *   <li>{@link RetentionType#SIZE}  – keep the newest files until their total size
 *       is below the configured threshold.</li>
 * </ul>
 * Use the factory methods {@link #keepLastN}, {@link #keepDays}, and
 * {@link #keepSize} to construct a policy without touching the builder directly.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupPolicy {

    private RetentionType type;

    /** Number of files to retain; used when {@code type == COUNT}. */
    private Integer keepLastN;

    /** Age threshold in days; used when {@code type == TIME}. */
    private Integer keepDays;

    /** Maximum combined size in bytes to retain; used when {@code type == SIZE}. */
    private Long maxTotalSizeBytes;

    // -------------------------------------------------------------------------
    // Retention strategy
    // -------------------------------------------------------------------------

    public enum RetentionType {
        /** Keep the last N files, deleting the oldest ones first. */
        COUNT,
        /** Keep files modified within the last N days. */
        TIME,
        /** Keep the newest files until the total retained size is below the threshold. */
        SIZE
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a policy that retains the {@code count} most recently modified files.
     *
     * @param count number of files to keep
     * @return configured cleanup policy
     */
    public static CleanupPolicy keepLastN(int count) {
        return CleanupPolicy.builder()
                .type(RetentionType.COUNT)
                .keepLastN(count)
                .build();
    }

    /**
     * Creates a policy that retains files modified within the last {@code days} days.
     *
     * @param days age threshold in days
     * @return configured cleanup policy
     */
    public static CleanupPolicy keepDays(int days) {
        return CleanupPolicy.builder()
                .type(RetentionType.TIME)
                .keepDays(days)
                .build();
    }

    /**
     * Creates a policy that retains the newest files whose combined size does not
     * exceed {@code maxBytes}.
     *
     * @param maxBytes maximum total size in bytes to retain
     * @return configured cleanup policy
     */
    public static CleanupPolicy keepSize(long maxBytes) {
        return CleanupPolicy.builder()
                .type(RetentionType.SIZE)
                .maxTotalSizeBytes(maxBytes)
                .build();
    }
}

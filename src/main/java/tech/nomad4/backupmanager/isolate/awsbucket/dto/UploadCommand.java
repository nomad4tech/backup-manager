package tech.nomad4.backupmanager.isolate.awsbucket.dto;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Paths;

/**
 * Parameters for a single file upload to an S3-compatible bucket.
 * <p>
 * The remote object key is computed as {@code destinationDirectory + "/" + filename},
 * where {@code filename} is the last component of {@link #localFilePath}.
 * If {@link #destinationDirectory} is empty or {@code null}, the filename alone
 * is used as the key (uploaded to the bucket root).
 * </p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * UploadCommand command = UploadCommand.builder()
 *     .config(BucketConfig.builder()
 *         .bucketName("my-backups")
 *         .region("us-east-1")
 *         .accessKey("AKIA...")
 *         .secretKey("secret")
 *         .build())
 *     .localFilePath("/var/backups/mydb_2025-01-01.sql.gz")
 *     .destinationDirectory("postgres/mydb")
 *     .build();
 * // → remote key: "postgres/mydb/mydb_2025-01-01.sql.gz"
 * }</pre>
 */
@Data
@Builder
public class UploadCommand {

    /**
     * Bucket connection and authentication settings.
     */
    private BucketConfig config;

    /**
     * Absolute path to the local file to upload.
     */
    private String localFilePath;

    /**
     * Directory prefix inside the bucket (no leading or trailing slash needed).
     * May be {@code null} or empty to place the file at the bucket root.
     */
    private String destinationDirectory;

    /**
     * Computes the remote object key for this upload.
     *
     * @return the S3 object key, e.g. {@code "postgres/mydb/mydb_2025-01-01.sql.gz"}
     */
    public String resolveRemoteKey() {
        String filename = Paths.get(localFilePath).getFileName().toString();
        if (destinationDirectory == null || destinationDirectory.isBlank()) {
            return filename;
        }
        String dir = destinationDirectory.replaceAll("^/+|/+$", "");
        return dir.isEmpty() ? filename : dir + "/" + filename;
    }
}

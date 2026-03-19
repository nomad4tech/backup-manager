package tech.nomad4.backupmanager.isolate.awsbucket.exception;

/**
 * Thrown when an S3 upload cannot be initiated or completed.
 * <p>
 * This is an unchecked exception. Callers that need to handle upload
 * failures without exceptions should use {@link tech.nomad4.backupmanager.isolate.awsbucket.service.AwsBucketService#upload},
 * which catches all errors and returns an {@link tech.nomad4.backupmanager.isolate.awsbucket.dto.UploadResult}
 * with {@code success = false} instead of throwing.
 * </p>
 */
public class UploadException extends RuntimeException {

    public UploadException(String message) {
        super(message);
    }

    public UploadException(String message, Throwable cause) {
        super(message, cause);
    }
}

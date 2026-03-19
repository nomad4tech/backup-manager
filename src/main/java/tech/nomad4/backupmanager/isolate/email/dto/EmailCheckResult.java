package tech.nomad4.backupmanager.isolate.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of an SMTP connectivity and authentication check.
 * <p>
 * Returned by
 * {@link tech.nomad4.backupmanager.isolate.email.service.EmailService#checkConnection}
 * to indicate whether the provided {@link EmailClientConfig} is reachable and valid.
 * </p>
 * <p>
 * Check {@link #isReachable()} first. On failure {@link #errorMessage} contains
 * a human-readable description of the problem (wrong credentials, unknown host,
 * network error, etc.).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCheckResult {

    /** SMTP host that was checked. */
    private String host;

    /** {@code true} if the SMTP server is reachable and the credentials are accepted. */
    private boolean reachable;

    /** Human-readable error description; {@code null} on success. */
    private String errorMessage;
}

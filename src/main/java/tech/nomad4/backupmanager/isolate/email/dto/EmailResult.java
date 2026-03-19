package tech.nomad4.backupmanager.isolate.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Outcome of an email send attempt.
 * <p>
 * Check {@link #isSuccess()} first. On failure {@link #errorMessage} describes
 * the cause (SMTP auth error, unreachable host, invalid address, etc.).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResult {

    /** Recipients the message was addressed to. */
    private List<String> recipients;

    /** Subject of the message that was sent. */
    private String subject;

    /** {@code true} if the SMTP server accepted the message for delivery. */
    private boolean success;

    /** Human-readable error description; {@code null} on success. */
    private String errorMessage;
}

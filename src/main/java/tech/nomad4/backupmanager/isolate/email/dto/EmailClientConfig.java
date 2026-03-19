package tech.nomad4.backupmanager.isolate.email.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * SMTP connection and authentication settings for the email service.
 * <p>
 * Typical port / security combinations:
 * <ul>
 *   <li>port 465, {@code ssl = true} - SMTP over SSL (SMTPS)</li>
 *   <li>port 587, {@code startTls = true} - SMTP with STARTTLS upgrade</li>
 *   <li>port 25, both false - plain SMTP (no encryption, not recommended)</li>
 * </ul>
 * </p>
 */
@Getter
@Builder
public class EmailClientConfig {

    /** SMTP server hostname or IP address. */
    private final String host;

    /** SMTP server port. */
    private final int port;

    /** SMTP authentication username. */
    private final String username;

    /** SMTP authentication password. */
    private final String password;

    /**
     * Envelope sender address shown in the {@code From} header.
     * Example: {@code "Backup Manager <noreply@example.com>"}.
     */
    private final String from;

    /**
     * Use SSL/TLS from the very first connection (SMTPS, typically port 465).
     * Mutually exclusive with {@link #startTls}.
     */
    private final boolean ssl;

    /**
     * Upgrade the plain connection to TLS using STARTTLS (typically port 587).
     * Mutually exclusive with {@link #ssl}.
     */
    private final boolean startTls;

    /**
     * Socket connect and read timeout in milliseconds.
     * {@code 0} means no timeout (wait indefinitely).
     */
    @Builder.Default
    private final int timeoutMs = 10_000;
}

package tech.nomad4.backupmanager.isolate.email.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.email.dto.EmailCheckResult;
import tech.nomad4.backupmanager.isolate.email.dto.EmailClientConfig;
import tech.nomad4.backupmanager.isolate.email.dto.EmailMessage;
import tech.nomad4.backupmanager.isolate.email.dto.EmailResult;

import java.util.List;
import java.util.Properties;

/**
 * Sends emails via SMTP using Jakarta Mail.
 * <p>
 * This service is intentionally free of Spring dependencies. Instantiate it
 * once with the connection settings and reuse across calls - a Jakarta Mail
 * {@link Session} is created once in the constructor and reused for every send.
 * </p>
 * <p>
 * {@link #send} never throws; all errors are captured and returned as an
 * {@link EmailResult} with {@code success = false} and a description in
 * {@link EmailResult#getErrorMessage()}.
 * </p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * EmailService emailService = new EmailService(
 *     EmailClientConfig.builder()
 *         .host("smtp.example.com")
 *         .port(587)
 *         .username("user@example.com")
 *         .password("secret")
 *         .from("Backup Manager <noreply@example.com>")
 *         .startTls(true)
 *         .build()
 * );
 *
 * EmailResult result = emailService.send(
 *     EmailMessage.builder()
 *         .to("admin@example.com")
 *         .subject("Backup completed")
 *         .body("Database dump finished successfully.")
 *         .build()
 * );
 *
 * if (!result.isSuccess()) {
 *     log.error("Failed to send email: {}", result.getErrorMessage());
 * }
 * }</pre>
 */
@Slf4j
public class EmailService {

    /**
     * Sends the given message to all addresses in {@link EmailMessage#getTo()}.
     * <p>
     * Never throws - any SMTP or address error is captured and returned in the result.
     * </p>
     *
     * @param config  SMTP connection settings
     * @param message message to send - subject, body, recipients, and content type
     * @return send result: success flag and error description on failure
     */
    public EmailResult send(EmailClientConfig config, EmailMessage message) {
        List<String> recipients = message.getTo();

        log.info("Sending email: subject='{}', recipients={}, host={}:{}, ssl={}, startTls={}",
                message.getSubject(), recipients,
                config.getHost(), config.getPort(), config.isSsl(), config.isStartTls());

        Session session = buildSession(config);
        try {
            MimeMessage mime = buildMimeMessage(config, session, message);
            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
                transport.sendMessage(mime, mime.getAllRecipients());
            }

            log.info("Email sent successfully: subject='{}', recipients={}",
                    message.getSubject(), recipients);

            return EmailResult.builder()
                    .recipients(recipients)
                    .subject(message.getSubject())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to send email: subject='{}', recipients={}: {}",
                    message.getSubject(), recipients, e.getMessage(), e);

            return EmailResult.builder()
                    .recipients(recipients)
                    .subject(message.getSubject())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Checks whether the SMTP server described by {@code config} is reachable and
     * the credentials are accepted.
     * <p>
     * Opens a {@link Transport} connection without sending any message and closes
     * it immediately. The check covers network reachability of the SMTP host and,
     * when auth is configured, validity of the username and password.
     * </p>
     * <p>
     * Never throws - all errors are captured and returned as an
     * {@link EmailCheckResult} with {@code reachable = false}.
     * </p>
     *
     * @param config SMTP connection settings to verify
     * @return check result - reachable/unreachable and error message on failure
     */
    public static EmailCheckResult checkConnection(EmailClientConfig config) {
        log.info("Checking SMTP connection: host={}, port={}", config.getHost(), config.getPort());

        Session session = buildSession(config);
        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(config.getHost(), config.getUsername(), config.getPassword());

            log.info("SMTP connection check passed: host={}", config.getHost());

            return EmailCheckResult.builder()
                    .host(config.getHost())
                    .reachable(true)
                    .build();

        } catch (Exception e) {
            log.warn("SMTP connection check failed: host={}: {}", config.getHost(), e.getMessage());

            return EmailCheckResult.builder()
                    .host(config.getHost())
                    .reachable(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private MimeMessage buildMimeMessage(EmailClientConfig config, Session session,
                                         EmailMessage message) throws MessagingException {
        MimeMessage mime = new MimeMessage(session);

        mime.setFrom(new InternetAddress(config.getFrom()));

        for (String recipient : message.getTo()) {
            mime.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        }

        mime.setSubject(message.getSubject(), "UTF-8");

        String contentType = message.isHtml() ? "text/html; charset=UTF-8" : "text/plain; charset=UTF-8";
        mime.setContent(message.getBody(), contentType);

        return mime;
    }

    /**
     * Builds a Jakarta Mail {@link Session} from the given configuration.
     * <p>
     * Auth is enabled whenever {@link EmailClientConfig#getUsername()} is non-blank.
     * SSL and STARTTLS are configured based on explicit flags in the config,
     * with automatic smart defaults applied for Gmail / Google Workspace SMTP servers.
     * </p>
     * <p>
     * For Gmail/Google Workspace:
     * <ul>
     *   <li>If port is 587 (or 25/2525) → STARTTLS is automatically enabled and required</li>
     *   <li>If port is 465 → SSL is automatically enabled</li>
     * </ul>
     * This avoids the common "Must issue a STARTTLS command first" error without forcing
     * the caller to set STARTTLS flags manually for Google servers.
     * </p>
     * <p>
     * For non-Google servers the behavior follows the explicit {@code ssl} and {@code startTls}
     * flags from the config. If both are set, SSL takes precedence.
     * </p>
     *
     * @param config SMTP connection settings
     * @return configured Jakarta Mail Session ready for transport
     */
    private static Session buildSession(EmailClientConfig config) {
        Properties props = new Properties();

        props.put("mail.smtp.host", config.getHost());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        props.put("mail.smtp.timeout", String.valueOf(config.getTimeoutMs()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(config.getTimeoutMs()));

        boolean hasAuth = config.getUsername() != null && !config.getUsername().isBlank();
        props.put("mail.smtp.auth", String.valueOf(hasAuth));

        String hostLower = config.getHost().toLowerCase().trim();
        boolean isGoogleSmtp = hostLower.contains("gmail.com") ||
                hostLower.contains("google.com") ||
                hostLower.equals("smtp.gmail.com") ||
                hostLower.equals("smtp-relay.gmail.com") ||
                hostLower.equals("aspmx.l.google.com") ||
                hostLower.endsWith(".google.com");

        if (isGoogleSmtp) {
            // Automatic STARTTLS for standard submission ports
            if (config.getPort() == 587 || config.getPort() == 25 || config.getPort() == 2525) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            // Automatic SSL for SMTPS port
            else if (config.getPort() == 465) {
                props.put("mail.smtp.ssl.enable", "true");
            }
            // If user explicitly set flags → they take precedence over auto-detection
            if (config.isSsl()) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.starttls.enable", "false");
            }
            if (config.isStartTls()) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
        }
        // Non-Google servers - strictly follow user config
        else {
            if (config.isSsl()) {
                props.put("mail.smtp.ssl.enable", "true");
            }
            if (config.isStartTls()) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
        }

        Authenticator authenticator = hasAuth
                ? new PasswordAuthentication(config.getUsername(), config.getPassword())
                : null;

        return Session.getInstance(props, authenticator);
    }

    /**
     * Wraps username/password into a Jakarta Mail {@link Authenticator}.
     */
    private static class PasswordAuthentication extends Authenticator {

        private final String username;
        private final String password;

        PasswordAuthentication(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
            return new jakarta.mail.PasswordAuthentication(username, password);
        }
    }
}

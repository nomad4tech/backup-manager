package tech.nomad4.backupmanager.isolate.email.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * An outgoing email message.
 * <p>
 * At least one recipient in {@link #to} is required.
 * Set {@link #html} to {@code true} when {@link #body} contains HTML markup;
 * the MIME content type will be set to {@code text/html} accordingly.
 * </p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * EmailMessage message = EmailMessage.builder()
 *     .to("admin@example.com")
 *     .to("ops@example.com")
 *     .subject("Backup completed")
 *     .body("Database dump finished successfully.")
 *     .build();
 * }</pre>
 */
@Data
@Builder
public class EmailMessage {

    /**
     * Primary recipients ({@code To} header). At least one address is required.
     * Use the {@code @Singular} builder method {@code .to("addr")} to add recipients one by one.
     */
    @Singular("to")
    private List<String> to;

    /** Email subject line. */
    private String subject;

    /** Email body - plain text or HTML depending on {@link #html}. */
    private String body;

    /**
     * When {@code true}, the body is sent as {@code text/html; charset=UTF-8}.
     * When {@code false} (default), the body is sent as {@code text/plain; charset=UTF-8}.
     */
    @Builder.Default
    private boolean html = false;
}

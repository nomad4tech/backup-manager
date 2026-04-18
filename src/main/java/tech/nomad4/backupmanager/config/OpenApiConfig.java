package tech.nomad4.backupmanager.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the Backup Manager REST API.
 * <p>
 * Configures the API metadata displayed in the Swagger UI, accessible
 * at {@code /swagger-ui.html} when the application is running.
 * </p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates the OpenAPI specification bean with API metadata.
     *
     * @return the configured OpenAPI specification
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Backup Manager API")
                        .description("REST API for managing Docker socket connections. " +
                                "Supports local Unix socket and remote SSH-tunneled connections " +
                                "to Docker hosts with automatic socat relay management.")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("nomad4tech")
                                .email("alex.sav4387@gmail.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("GitHub Repository")
                        .url("https://github.com/nomad4tech/backup-manager"));
    }
}

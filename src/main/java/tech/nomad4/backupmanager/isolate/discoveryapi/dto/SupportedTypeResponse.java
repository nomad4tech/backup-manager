package tech.nomad4.backupmanager.isolate.discoveryapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tech.nomad4.backupmanager.discovery.entity.DatabaseType;

/**
 * Represents a database type supported by the discovery service.
 */
@Getter
@RequiredArgsConstructor
@Schema(description = "Supported database type")
public class SupportedTypeResponse {

    @Schema(description = "Enum name used in API requests", example = "POSTGRES")
    private final String name;

    @Schema(description = "Human-readable name", example = "PostgreSQL")
    private final String displayName;

    public static SupportedTypeResponse from(DatabaseType type) {
        return new SupportedTypeResponse(type.name(), type.getDisplayName());
    }
}

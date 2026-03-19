package tech.nomad4.backupmanager.isolate.discoveryapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.discovery.entity.ContainerInfo;
import tech.nomad4.backupmanager.discovery.entity.ContainerState;
import tech.nomad4.backupmanager.discovery.entity.DatabaseType;

import java.util.List;

/**
 * Lightweight container info returned by the list endpoint.
 * Does not include environment variables (use the detail endpoint for that).
 */
@Getter
@Builder
@Schema(description = "Database container summary")
public class ContainerSummaryResponse {

    @Schema(description = "Short container ID (12 chars)", example = "a1b2c3d4e5f6")
    private final String containerId;

    @Schema(description = "Container name without leading slash", example = "my-postgres")
    private final String containerName;

    @Schema(description = "Detected database type", example = "POSTGRES")
    private final DatabaseType databaseType;

    @Schema(description = "Human-readable database type name", example = "PostgreSQL")
    private final String databaseTypeName;

    @Schema(description = "Docker image name with tag", example = "postgres:15-alpine")
    private final String imageName;

    @Schema(description = "Image version tag", example = "15-alpine")
    private final String imageVersion;

    @Schema(description = "Current container state", example = "RUNNING")
    private final ContainerState state;

    @Schema(description = "Publicly exposed port numbers", example = "[5432]")
    private final List<Integer> exposedPorts;

    @Schema(description = "Unix timestamp (seconds) when the container was created")
    private final Long createdAt;

    public static ContainerSummaryResponse from(ContainerInfo info) {
        return ContainerSummaryResponse.builder()
                .containerId(info.getContainerId())
                .containerName(info.getContainerName())
                .databaseType(info.getDatabaseType())
                .databaseTypeName(info.getDatabaseType().getDisplayName())
                .imageName(info.getImageName())
                .imageVersion(info.getImageVersion())
                .state(info.getState())
                .exposedPorts(info.getExposedPorts())
                .createdAt(info.getCreatedAt())
                .build();
    }
}

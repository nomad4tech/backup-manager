package tech.nomad4.backupmanager.discoveryapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.isolate.discovery.entity.ContainerInfo;
import tech.nomad4.backupmanager.isolate.discovery.entity.ContainerState;
import tech.nomad4.backupmanager.isolate.discovery.entity.DatabaseType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Full container info returned by the detail (inspect) endpoint.
 * <p>
 * Includes environment variables with sensitive values masked
 * (keys containing PASSWORD, SECRET, KEY, TOKEN, PASS).
 * </p>
 */
@Getter
@Builder
@Schema(description = "Full database container information from Docker inspect")
public class ContainerDetailResponse {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "key", "token", "pass", "pwd"
    );

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

    @Schema(description = "Container labels")
    private final Map<String, String> labels;

    @Schema(description = "Environment variables (sensitive values masked with ***)")
    private final Map<String, String> env;

    @Schema(description = "Unix timestamp (seconds) when the container was created")
    private final Long createdAt;

    @Schema(description = "Unix timestamp (seconds) when the container was last started")
    private final Long startedAt;

    public static ContainerDetailResponse from(ContainerInfo info) {
        return ContainerDetailResponse.builder()
                .containerId(info.getContainerId())
                .containerName(info.getContainerName())
                .databaseType(info.getDatabaseType())
                .databaseTypeName(info.getDatabaseType().getDisplayName())
                .imageName(info.getImageName())
                .imageVersion(info.getImageVersion())
                .state(info.getState())
                .exposedPorts(info.getExposedPorts())
                .labels(info.getLabels())
                .env(maskSensitiveEnv(info.getEnv()))
                .createdAt(info.getCreatedAt())
                .startedAt(info.getStartedAt())
                .build();
    }

    private static Map<String, String> maskSensitiveEnv(Map<String, String> env) {
        if (env == null) return Map.of();
        return env.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> isSensitive(e.getKey()) ? "***" : e.getValue()
                ));
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }
}

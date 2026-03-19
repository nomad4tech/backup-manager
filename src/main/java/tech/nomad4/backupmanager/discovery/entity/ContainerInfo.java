package tech.nomad4.backupmanager.discovery.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents detailed information about a discovered Docker database container.
 * <p>
 * Contains all relevant metadata extracted from Docker's container inspection,
 * including identification, database type detection, runtime state, networking,
 * and configuration details.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInfo {

    /** Short container ID (first 12 characters). */
    private String containerId;

    /** Container name without the leading slash. */
    private String containerName;

    /** Detected database type based on the container image. */
    private DatabaseType databaseType;

    /** Image name with tag (e.g., "postgres:15-alpine"). */
    private String imageName;

    /** Version extracted from the image tag (e.g., "15", "latest"). */
    private String imageVersion;

    /** Current runtime state of the container. */
    private ContainerState state;

    /** List of publicly exposed port numbers. */
    private List<Integer> exposedPorts;

    /** Container labels as key-value pairs. */
    private Map<String, String> labels;

    /** Container environment variables as key-value pairs. */
    private Map<String, String> env;

    /** Unix timestamp (seconds) when the container was created. */
    private Long createdAt;

    /** Unix timestamp (seconds) when the container was last started. */
    private Long startedAt;
}

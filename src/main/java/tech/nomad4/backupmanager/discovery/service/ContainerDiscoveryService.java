package tech.nomad4.backupmanager.discovery.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.discovery.entity.ContainerInfo;
import tech.nomad4.backupmanager.discovery.entity.ContainerState;
import tech.nomad4.backupmanager.discovery.entity.DatabaseType;
import tech.nomad4.backupmanager.discovery.exception.ContainerDiscoveryException;
import tech.nomad4.backupmanager.discovery.filter.ContainerFilter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for discovering and inspecting Docker database containers.
 * <p>
 * Uses registered {@link ContainerFilter} implementations to identify database
 * containers. Accepts a {@link DockerClient} as a parameter to each method,
 * keeping this service decoupled from connection management.
 * </p>
 *
 * @see ContainerFilter
 */
@Slf4j
public class ContainerDiscoveryService {

    private final List<ContainerFilter> filters;

    public ContainerDiscoveryService(List<ContainerFilter> filters) {
        this.filters = filters;
    }

    /**
     * Finds all database containers accessible through the given Docker client.
     * <p>
     * Lists all containers (including stopped) and applies registered filters
     * to identify database containers. Only containers matching at least one
     * filter are returned.
     * </p>
     *
     * @param client the Docker client to query
     * @return list of discovered database containers
     * @throws ContainerDiscoveryException if the Docker API call fails
     */
    public List<ContainerInfo> findAllDatabaseContainers(DockerClient client) {
        log.info("Discovering database containers");

        List<Container> containers = listAllContainers(client);
        log.debug("Found {} total containers", containers.size());

        List<ContainerInfo> databaseContainers = containers.stream()
                .filter(this::isDatabaseContainer)
                .map(this::mapToContainerInfo)
                .collect(Collectors.toList());

        log.info("Discovered {} database containers", databaseContainers.size());
        return databaseContainers;
    }

    /**
     * Finds containers matching a specific database type.
     *
     * @param client the Docker client to query
     * @param type   the database type to filter by
     * @return list of containers matching the specified type
     * @throws ContainerDiscoveryException if the Docker API call fails
     */
    public List<ContainerInfo> findByDatabaseType(DockerClient client, DatabaseType type) {
        log.info("Finding {} containers", type.getDisplayName());

        List<Container> containers = listAllContainers(client);

        List<ContainerInfo> matched = containers.stream()
                .filter(c -> detectDatabaseType(c) == type)
                .map(this::mapToContainerInfo)
                .collect(Collectors.toList());

        log.info("Found {} {} containers", matched.size(), type.getDisplayName());
        return matched;
    }

    /**
     * Finds PostgreSQL containers (convenience method).
     *
     * @param client the Docker client to query
     * @return list of PostgreSQL containers
     * @throws ContainerDiscoveryException if the Docker API call fails
     */
    public List<ContainerInfo> findPostgresContainers(DockerClient client) {
        return findByDatabaseType(client, DatabaseType.POSTGRES);
    }

    /**
     * Gets detailed information about a specific container.
     * <p>
     * Uses the Docker inspect API to retrieve full container details including
     * environment variables and timestamps.
     * </p>
     *
     * @param client      the Docker client to query
     * @param containerId the container ID (short or long form)
     * @return detailed container information
     * @throws ContainerDiscoveryException if the container is not found or inspection fails
     */
    public ContainerInfo getContainerInfo(DockerClient client, String containerId) {
        log.info("Getting container info for {}", containerId);

        try {
            InspectContainerResponse details = client.inspectContainerCmd(containerId).exec();
            return mapFromInspection(details);

        } catch (ContainerDiscoveryException e) {
            throw e;
        } catch (Exception e) {
            throw new ContainerDiscoveryException(
                    "Failed to inspect container " + containerId, e);
        }
    }

    /**
     * Checks if a container is currently running.
     *
     * @param client      the Docker client to query
     * @param containerId the container ID (short or long form)
     * @return {@code true} if the container is in the RUNNING state
     * @throws ContainerDiscoveryException if the container status cannot be determined
     */
    public boolean isContainerRunning(DockerClient client, String containerId) {
        log.debug("Checking if container {} is running", containerId);

        try {
            InspectContainerResponse details = client.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = details.getState();

            return state != null && Boolean.TRUE.equals(state.getRunning());

        } catch (Exception e) {
            throw new ContainerDiscoveryException(
                    "Failed to check container status for " + containerId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Container> listAllContainers(DockerClient client) {
        try {
            return client.listContainersCmd()
                    .withShowAll(true)
                    .exec();
        } catch (ContainerDiscoveryException e) {
            throw e;
        } catch (Exception e) {
            throw new ContainerDiscoveryException("Failed to list containers", e);
        }
    }

    private boolean isDatabaseContainer(Container container) {
        return detectDatabaseType(container) != DatabaseType.UNKNOWN;
    }

    private DatabaseType detectDatabaseType(Container container) {
        for (ContainerFilter filter : filters) {
            if (filter.matches(container)) {
                return filter.getDatabaseType();
            }
        }
        return DatabaseType.fromImage(container.getImage());
    }

    private ContainerInfo mapToContainerInfo(Container container) {
        String image = container.getImage();
        String[] names = container.getNames();
        String name = (names != null && names.length > 0)
                ? cleanContainerName(names[0])
                : "";

        return ContainerInfo.builder()
                .containerId(truncateId(container.getId()))
                .containerName(name)
                .databaseType(detectDatabaseType(container))
                .imageName(image)
                .imageVersion(extractVersion(image))
                .state(ContainerState.fromDockerState(container.getState()))
                .exposedPorts(extractPorts(container.getPorts()))
                .labels(container.getLabels() != null ? container.getLabels() : Collections.emptyMap())
                .env(Collections.emptyMap())
                .createdAt(container.getCreated())
                .build();
    }

    private ContainerInfo mapFromInspection(InspectContainerResponse details) {
        String image = details.getConfig() != null ? details.getConfig().getImage() : "";
        String name = cleanContainerName(details.getName() != null ? details.getName() : "");

        InspectContainerResponse.ContainerState dockerState = details.getState();
        String stateStr = (dockerState != null && dockerState.getStatus() != null)
                ? dockerState.getStatus()
                : "unknown";

        Long startedAt = null;
        if (dockerState != null && dockerState.getStartedAt() != null) {
            startedAt = parseDockerTimestamp(dockerState.getStartedAt());
        }

        return ContainerInfo.builder()
                .containerId(truncateId(details.getId()))
                .containerName(name)
                .databaseType(DatabaseType.fromImage(image))
                .imageName(image)
                .imageVersion(extractVersion(image))
                .state(ContainerState.fromDockerState(stateStr))
                .exposedPorts(extractPortsFromInspection(details))
                .labels(details.getConfig() != null && details.getConfig().getLabels() != null
                        ? details.getConfig().getLabels()
                        : Collections.emptyMap())
                .env(parseEnvArray(details.getConfig() != null ? details.getConfig().getEnv() : null))
                .createdAt(parseDockerTimestamp(details.getCreated()))
                .startedAt(startedAt)
                .build();
    }

    private String extractVersion(String image) {
        if (image == null || image.isEmpty()) {
            return "latest";
        }

        // Handle images with registry prefix (e.g., registry.example.com/postgres:15)
        int colonIndex = image.lastIndexOf(':');
        if (colonIndex < 0 || colonIndex == image.length() - 1) {
            return "latest";
        }

        String tag = image.substring(colonIndex + 1);

        // If tag contains a slash, it's part of the registry path, not a version
        if (tag.contains("/")) {
            return "latest";
        }

        return tag;
    }

    private String cleanContainerName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return name.startsWith("/") ? name.substring(1) : name;
    }

    private String truncateId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private List<Integer> extractPorts(ContainerPort[] ports) {
        if (ports == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(ports)
                .filter(p -> p.getPublicPort() != null)
                .map(ContainerPort::getPublicPort)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Integer> extractPortsFromInspection(InspectContainerResponse details) {
        if (details.getNetworkSettings() == null || details.getNetworkSettings().getPorts() == null) {
            return Collections.emptyList();
        }

        Map<com.github.dockerjava.api.model.ExposedPort, com.github.dockerjava.api.model.Ports.Binding[]> bindings =
                details.getNetworkSettings().getPorts().getBindings();

        if (bindings == null) {
            return Collections.emptyList();
        }

        List<Integer> ports = new ArrayList<>();
        for (var entry : bindings.entrySet()) {
            if (entry.getValue() != null) {
                for (var binding : entry.getValue()) {
                    if (binding.getHostPortSpec() != null) {
                        try {
                            ports.add(Integer.parseInt(binding.getHostPortSpec()));
                        } catch (NumberFormatException e) {
                            log.debug("Could not parse port: {}", binding.getHostPortSpec());
                        }
                    }
                }
            }
        }
        return ports.stream().distinct().collect(Collectors.toList());
    }

    private Map<String, String> parseEnvArray(String[] envArray) {
        if (envArray == null) {
            return Collections.emptyMap();
        }
        Map<String, String> env = new LinkedHashMap<>();
        for (String entry : envArray) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                env.put(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
        return env;
    }

    private Long parseDockerTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty() || timestamp.startsWith("0001")) {
            return null;
        }
        try {
            java.time.Instant instant = java.time.Instant.parse(timestamp);
            return instant.getEpochSecond();
        } catch (Exception e) {
            log.debug("Could not parse Docker timestamp: {}", timestamp);
            return null;
        }
    }
}

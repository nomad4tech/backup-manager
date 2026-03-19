package tech.nomad4.backupmanager.isolate.discovery.filter;

import com.github.dockerjava.api.model.Container;
import tech.nomad4.backupmanager.isolate.discovery.entity.DatabaseType;
import tech.nomad4.backupmanager.isolate.discovery.service.ContainerDiscoveryService;

/**
 * Filter for identifying database containers by their Docker image.
 * <p>
 * Implementations define matching logic for a specific database type.
 * All registered filters are automatically injected into the
 * {@link ContainerDiscoveryService}
 * for container classification.
 * </p>
 */
public interface ContainerFilter {

    /**
     * Checks whether the given container matches this filter's criteria.
     *
     * @param container the Docker container to evaluate
     * @return {@code true} if the container matches this filter
     */
    boolean matches(Container container);

    /**
     * Returns the database type that this filter identifies.
     *
     * @return the associated {@link DatabaseType}
     */
    DatabaseType getDatabaseType();
}

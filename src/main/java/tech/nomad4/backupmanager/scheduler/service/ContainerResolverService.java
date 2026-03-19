package tech.nomad4.backupmanager.scheduler.service;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.discovery.entity.ContainerInfo;
import tech.nomad4.backupmanager.discovery.service.ContainerDiscoveryService;

/**
 * Resolves a container ID before each backup run, handling the case where
 * the container has been recreated (e.g. after {@code docker-compose down && up})
 * and therefore has a new ID.
 *
 * <p>Resolution algorithm:
 * <ol>
 *   <li>Inspect the container by its stored ID - if found, return it unchanged.</li>
 *   <li>If not found by ID <em>and</em> a container name is stored, inspect by name.</li>
 *   <li>If found by name, return the new ID and mark the result as changed so the
 *       caller can persist the update.</li>
 *   <li>If neither lookup succeeds, throw an {@link IllegalStateException} with a
 *       descriptive message.</li>
 * </ol>
 * </p>
 *
 * <p>This service is intentionally narrow: it only resolves IDs.
 * Persisting the updated ID back to the database is the caller's responsibility
 * (see {@link BackupExecutionOrchestrator}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerResolverService {

    private final ContainerDiscoveryService discoveryService;

    /**
     * Result of a container resolution attempt.
     *
     * @param resolvedId  the ID that should be used for the backup command
     * @param originalId  the ID that was stored in the database (may differ from resolvedId)
     * @param idChanged   {@code true} if the container was found under a different ID
     */
    public record ContainerResolution(String resolvedId, String originalId, boolean idChanged) {}

    /**
     * Attempts to resolve the current container ID.
     *
     * @param client        Docker client for the target host
     * @param storedId      container ID stored in {@link tech.nomad4.backupmanager.backuptask.entity.BackupTask}
     * @param containerName container name stored in BackupTask (may be {@code null} for legacy tasks)
     * @return resolution result - always non-null; check {@link ContainerResolution#idChanged()}
     *         to decide whether to persist the new ID
     * @throws IllegalStateException if the container cannot be found by either ID or name
     */
    public ContainerResolution resolve(DockerClient client, String storedId, String containerName) {

        // 1. Try by stored ID - the happy path
        try {
            ContainerInfo info = discoveryService.getContainerInfo(client, storedId);
            log.debug("Container resolved by ID: {}", storedId);
            return new ContainerResolution(info.getContainerId(), storedId, false);
        } catch (Exception e) {
            log.debug("Container '{}' not found by ID ({}), trying name fallback", storedId, e.getMessage());
        }

        // 2. Try by stored name (container was recreated, new ID assigned)
        if (containerName == null || containerName.isBlank()) {
            throw new IllegalStateException(
                    "Container '" + storedId + "' not found and no container name is stored for fallback. " +
                    "Re-save the backup task so the container name is captured.");
        }

        try {
            ContainerInfo found = discoveryService.getContainerInfo(client, containerName);
            String newId = found.getContainerId();
            log.warn(
                    "Container ID change detected - name='{}', old ID='{}', new ID='{}'. " +
                    "The task will be updated automatically.",
                    containerName, storedId, newId);
            return new ContainerResolution(newId, storedId, true);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Container not found by stored ID '" + storedId +
                    "' or by name '" + containerName + "'. " +
                    "The container may have been removed or renamed.", e);
        }
    }
}

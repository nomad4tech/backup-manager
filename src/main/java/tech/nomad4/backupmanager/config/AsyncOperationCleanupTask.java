package tech.nomad4.backupmanager.config;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.nomad4.backupmanager.isolate.command.executor.AsyncOperationManager;

/**
 * Scheduled wrapper that periodically triggers {@link AsyncOperationManager#cleanup()}.
 * <p>
 * {@code AsyncOperationManager} is a plain Java class with no Spring dependency,
 * so scheduling lives here in the application layer.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AsyncOperationCleanupTask {

    private final AsyncOperationManager asyncOperationManager;

    @Scheduled(fixedDelayString = "${backup-manager.command.cleanup-interval:300000}")
    public void run() {
        asyncOperationManager.cleanup();
    }
}

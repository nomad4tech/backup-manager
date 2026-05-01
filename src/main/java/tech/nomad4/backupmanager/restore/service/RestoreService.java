package tech.nomad4.backupmanager.restore.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.backuphistory.entity.BackupRecord;
import tech.nomad4.backupmanager.backuphistory.repository.BackupRecordRepository;
import tech.nomad4.backupmanager.isolate.discovery.entity.ContainerInfo;
import tech.nomad4.backupmanager.isolate.discovery.service.ContainerDiscoveryService;
import tech.nomad4.backupmanager.restore.dto.CreateRestoreRequest;
import tech.nomad4.backupmanager.restore.dto.DbExistsResponse;
import tech.nomad4.backupmanager.restore.entity.RestoreRecord;
import tech.nomad4.backupmanager.restore.entity.RestoreStatus;
import tech.nomad4.backupmanager.restore.repository.RestoreRecordRepository;
import tech.nomad4.backupmanager.socketmanagement.service.DockerSocketFacadeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestoreService {

    private final RestoreRecordRepository restoreRecordRepository;
    private final BackupRecordRepository backupRecordRepository;
    private final RestoreExecutionOrchestrator orchestrator;
    private final DockerSocketFacadeService socketFacade;
    private final ContainerDiscoveryService containerDiscoveryService;

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public Page<RestoreRecord> findAll(int page, int size) {
        return restoreRecordRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
    }

    public RestoreRecord findById(Long id) {
        return restoreRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Restore record not found: " + id));
    }

    /** Returns all backup records that have a file on disk. */
    public List<BackupRecord> findAvailableBackups() {
        return backupRecordRepository.findByFilePathNotNullOrderByStartedAtDesc();
    }

    /** Returns containers of the given database type reachable via the given socket. */
    public List<ContainerInfo> findCompatibleContainers(Long socketId, String dbType) {
        DockerClient client = socketFacade.getDockerClient(socketId);
        tech.nomad4.backupmanager.isolate.discovery.entity.DatabaseType type =
                tech.nomad4.backupmanager.isolate.discovery.entity.DatabaseType.valueOf(dbType.toUpperCase());
        return containerDiscoveryService.findByDatabaseType(client, type);
    }

    /**
     * Checks whether a database with the given name already exists inside the container.
     * Runs a quick exec (10-second timeout) and inspects the exit code.
     * Never throws — returns exists=false with an error message on any failure.
     */
    public DbExistsResponse checkDatabaseExists(Long socketId, String containerId,
                                                String dbName, String dbType) {
        try {
            DockerClient client = socketFacade.getDockerClient(socketId);
            String[] cmd = buildExistsCheckCommand(dbName, dbType);

            ExecCreateCmdResponse exec = client.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(cmd)
                    .exec();

            client.execStartCmd(exec.getId())
                    .withDetach(false)
                    .withTty(false)
                    .exec(new ExecStartResultCallback())
                    .awaitCompletion(10, TimeUnit.SECONDS);

            InspectExecResponse inspect = client.inspectExecCmd(exec.getId()).exec();
            Long exitCode = inspect.getExitCodeLong();
            boolean exists = exitCode != null && exitCode == 0;

            return DbExistsResponse.builder()
                    .exists(exists)
                    .databaseName(dbName)
                    .message(exists
                            ? "Database '" + dbName + "' exists in the container"
                            : "Database '" + dbName + "' does not exist in the container")
                    .build();

        } catch (Exception e) {
            log.warn("Database existence check failed for '{}' in container {}: {}",
                    dbName, containerId, e.getMessage());
            return DbExistsResponse.builder()
                    .exists(false)
                    .databaseName(dbName)
                    .message("Check failed: " + e.getMessage())
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Validates the request, creates a {@code PENDING} restore record, then kicks off
     * the restore asynchronously. Returns immediately with the pending record.
     */
    public RestoreRecord createAndRun(CreateRestoreRequest request) {
        BackupRecord backupRecord = backupRecordRepository.findById(request.getBackupRecordId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Backup record not found: " + request.getBackupRecordId()));

        if (backupRecord.getFilePath() == null) {
            throw new IllegalArgumentException("Backup file no longer exists on disk");
        }
        if (backupRecord.getDatabaseType() == null) {
            throw new IllegalArgumentException(
                    "Backup record has no database type: " + request.getBackupRecordId());
        }

        RestoreRecord record = new RestoreRecord();
        record.setBackupRecordId(request.getBackupRecordId());
        record.setBackupFilePath(backupRecord.getFilePath());
        record.setSocketId(request.getSocketId());
        record.setContainerId(request.getContainerId());
        record.setContainerName(request.getContainerName());
        record.setSourceDatabaseName(backupRecord.getDatabaseName());
        record.setTargetDatabaseName(request.getTargetDatabaseName());
        record.setDatabaseType(backupRecord.getDatabaseType());
        record.setStartedAt(LocalDateTime.now());
        record.setStatus(RestoreStatus.PENDING);

        RestoreRecord saved = restoreRecordRepository.save(record);

        CompletableFuture.runAsync(() -> orchestrator.executeRestore(saved.getId()));

        return saved;
    }

    public void cancel(Long id) {
        orchestrator.cancelRestore(id);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String[] buildExistsCheckCommand(String dbName, String dbType) {
        if ("POSTGRES".equalsIgnoreCase(dbType)) {
            return new String[]{
                    "sh", "-c",
                    "psql -U \"$POSTGRES_USER\" -lqt | cut -d'|' -f1 | grep -qw '" + dbName + "'"
            };
        }
        // MYSQL / MARIADB
        return new String[]{
                "sh", "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -u root -e \"SHOW DATABASES LIKE '" +
                        dbName + "'\" | grep -q '" + dbName + "'"
        };
    }
}

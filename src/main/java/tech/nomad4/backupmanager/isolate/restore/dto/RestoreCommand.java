package tech.nomad4.backupmanager.isolate.restore.dto;

import lombok.Builder;
import lombok.Getter;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;

@Builder
@Getter
public class RestoreCommand {
    private String containerId;
    private String databaseName;
    private String inputFilePath;
    private DatabaseType databaseType;
    private boolean compressed;
    private int timeoutSeconds;
}

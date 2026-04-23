package tech.nomad4.backupmanager.isolate.backup.strategy;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.nomad4.backupmanager.isolate.backup.dto.BackupCommand;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.backup.exception.BackupException;

/**
 * MariaDB backup strategy — delegates dump execution to {@link MysqlBackupStrategy}.
 * <p>
 * MariaDB images expose {@code mysqldump} as an alias for {@code mariadb-dump} and
 * honour {@code $MYSQL_ROOT_PASSWORD}, so the MySQL dump logic works unchanged.
 * This class exists solely to register a strategy for {@link DatabaseType#MARIADB}
 * so that the strategy dispatcher does not throw for MariaDB tasks.
 * </p>
 */
@Slf4j
public class MariadbBackupStrategy implements BackupStrategy {

    private final MysqlBackupStrategy delegate;

    public MariadbBackupStrategy(MysqlBackupStrategy mysqlBackupStrategy) {
        this.delegate = mysqlBackupStrategy;
    }

    @Override
    public DatabaseType getSupportedType() {
        return DatabaseType.MARIADB;
    }

    @Override
    public void execute(DockerClient dockerClient, BackupCommand command) throws BackupException {
        delegate.execute(dockerClient, command);
    }
}

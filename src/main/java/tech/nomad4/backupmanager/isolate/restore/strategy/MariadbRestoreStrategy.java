package tech.nomad4.backupmanager.isolate.restore.strategy;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.backup.dto.DatabaseType;
import tech.nomad4.backupmanager.isolate.restore.dto.RestoreCommand;
import tech.nomad4.backupmanager.isolate.restore.exception.RestoreException;

/**
 * MariaDB restore strategy — delegates restore execution to {@link MysqlRestoreStrategy}.
 * <p>
 * MariaDB images honour {@code $MYSQL_ROOT_PASSWORD} and ship with a {@code mysql}
 * client compatible with the MySQL restore logic, so no separate implementation is needed.
 * This class exists solely to register a strategy for {@link DatabaseType#MARIADB}.
 * </p>
 */
@Slf4j
public class MariadbRestoreStrategy implements RestoreStrategy {

    private final MysqlRestoreStrategy delegate;

    public MariadbRestoreStrategy(MysqlRestoreStrategy mysqlRestoreStrategy) {
        this.delegate = mysqlRestoreStrategy;
    }

    @Override
    public DatabaseType getSupportedType() {
        return DatabaseType.MARIADB;
    }

    @Override
    public void execute(DockerClient dockerClient, RestoreCommand command) throws RestoreException {
        delegate.execute(dockerClient, command);
    }
}

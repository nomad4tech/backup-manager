package tech.nomad4.backupmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.nomad4.backupmanager.isolate.awsbucket.service.AwsBucketService;
import tech.nomad4.backupmanager.isolate.backup.service.BackupExecutionService;
import tech.nomad4.backupmanager.isolate.backup.strategy.BackupStrategy;
import tech.nomad4.backupmanager.isolate.backup.strategy.MysqlBackupStrategy;
import tech.nomad4.backupmanager.isolate.backup.strategy.PostgresBackupStrategy;
import tech.nomad4.backupmanager.isolate.email.service.EmailService;
import tech.nomad4.backupmanager.isolate.command.executor.AsyncOperationManager;
import tech.nomad4.backupmanager.isolate.command.executor.DockerExecExecutor;
import tech.nomad4.backupmanager.isolate.command.service.MysqlCommandService;
import tech.nomad4.backupmanager.isolate.command.service.PostgresCommandService;
import tech.nomad4.backupmanager.discovery.filter.ContainerFilter;
import tech.nomad4.backupmanager.discovery.filter.PostgresContainerFilter;
import tech.nomad4.backupmanager.discovery.service.ContainerDiscoveryService;
import tech.nomad4.backupmanager.isolate.socket.service.DockerSocketService;
import tech.nomad4.backupmanager.isolate.storage.service.StorageService;

import java.util.List;

/**
 * Spring wiring for packages that are intentionally Spring-free (socket, discovery,
 * command, storage, backup). All classes in those packages are plain Java - no
 * {@code @Service} / {@code @Component} annotations. This configuration class is
 * the single integration point that registers them as Spring beans.
 *
 * <p>To extract a package into a standalone library, simply remove the
 * corresponding {@code @Bean} method and wire the object yourself.</p>
 */
@Configuration
public class CoreBeansConfig {

    // -------------------------------------------------------------------------
    // socket
    // -------------------------------------------------------------------------

    @Bean(destroyMethod = "close")
    public DockerSocketService dockerSocketService() {
        return new DockerSocketService();
    }

    // -------------------------------------------------------------------------
    // discovery
    // -------------------------------------------------------------------------

    @Bean
    public PostgresContainerFilter postgresContainerFilter() {
        return new PostgresContainerFilter();
    }

    @Bean
    public ContainerDiscoveryService containerDiscoveryService(List<ContainerFilter> filters) {
        return new ContainerDiscoveryService(filters);
    }

    // -------------------------------------------------------------------------
    // command
    // -------------------------------------------------------------------------

    @Bean
    public DockerExecExecutor dockerExecExecutor() {
        return new DockerExecExecutor();
    }

    @Bean(destroyMethod = "close")
    public AsyncOperationManager asyncOperationManager(
            @Value("${backup-manager.command.max-concurrent:10}") int maxConcurrent,
            @Value("${backup-manager.command.default-ttl:3600}") long defaultTtl) {
        return new AsyncOperationManager(maxConcurrent, defaultTtl);
    }

    @Bean
    public PostgresCommandService postgresCommandService(
            DockerExecExecutor executor,
            AsyncOperationManager operationManager,
            @Value("${backup-manager.command.default-timeout:3600}") long defaultTimeout) {
        return new PostgresCommandService(executor, operationManager, defaultTimeout);
    }

    @Bean
    public MysqlCommandService mysqlCommandService(
            DockerExecExecutor executor,
            AsyncOperationManager operationManager,
            @Value("${backup-manager.command.default-timeout:3600}") long defaultTimeout) {
        return new MysqlCommandService(executor, operationManager, defaultTimeout);
    }

    // -------------------------------------------------------------------------
    // storage
    // -------------------------------------------------------------------------

    @Bean
    public StorageService storageService() {
        return new StorageService();
    }

    // -------------------------------------------------------------------------
    // backup
    // -------------------------------------------------------------------------

    @Bean
    public PostgresBackupStrategy postgresBackupStrategy() {
        return new PostgresBackupStrategy();
    }

    @Bean
    public MysqlBackupStrategy mysqlBackupStrategy() {
        return new MysqlBackupStrategy();
    }

    @Bean
    public BackupExecutionService backupExecutionService(List<BackupStrategy> strategies) {
        return new BackupExecutionService(strategies);
    }

    // -------------------------------------------------------------------------
    // isolate - aws / email
    // -------------------------------------------------------------------------

    @Bean
    public AwsBucketService awsBucketService() {
        return new AwsBucketService();
    }

    @Bean
    public EmailService emailService() {
        return new EmailService();
    }

}

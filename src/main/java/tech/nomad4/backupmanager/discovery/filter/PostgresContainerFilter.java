package tech.nomad4.backupmanager.discovery.filter;

import com.github.dockerjava.api.model.Container;
import tech.nomad4.backupmanager.discovery.entity.DatabaseType;

/**
 * Filter that identifies PostgreSQL database containers.
 * <p>
 * Matches containers whose image name contains "postgres" or "postgresql",
 * covering official PostgreSQL images and common variants.
 * </p>
 */
public class PostgresContainerFilter implements ContainerFilter {

    @Override
    public boolean matches(Container container) {
        String image = container.getImage().toLowerCase();
        return image.contains("postgres") || image.contains("postgresql");
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRES;
    }
}

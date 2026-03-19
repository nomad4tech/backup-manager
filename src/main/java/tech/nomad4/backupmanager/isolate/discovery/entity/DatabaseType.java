package tech.nomad4.backupmanager.isolate.discovery.entity;

import lombok.Getter;

/**
 * Supported database types that can be detected from Docker container images.
 * <p>
 * Each type maps to a known Docker image name pattern (e.g., "postgres", "mysql")
 * and provides a human-readable display name. The {@link #fromImage(String)} method
 * performs case-insensitive matching against image names to identify database types.
 * </p>
 */
@Getter
public enum DatabaseType {

    POSTGRES("postgres", "PostgreSQL"),
    MYSQL("mysql", "MySQL"),
    MARIADB("mariadb", "MariaDB"),
    MONGODB("mongo", "MongoDB"),
    REDIS("redis", "Redis"),
    UNKNOWN("unknown", "Unknown");

    private final String imageName;
    private final String displayName;

    DatabaseType(String imageName, String displayName) {
        this.imageName = imageName;
        this.displayName = displayName;
    }

    /**
     * Detects the database type from a Docker image name.
     * <p>
     * Performs a case-insensitive substring match against each known database
     * image pattern. Returns {@link #UNKNOWN} if no match is found.
     * </p>
     *
     * @param imageName the Docker image name (e.g., "postgres:15-alpine")
     * @return the detected database type, or {@link #UNKNOWN}
     */
    public static DatabaseType fromImage(String imageName) {
        String lower = imageName.toLowerCase();
        for (DatabaseType type : values()) {
            if (type != UNKNOWN && lower.contains(type.imageName)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}

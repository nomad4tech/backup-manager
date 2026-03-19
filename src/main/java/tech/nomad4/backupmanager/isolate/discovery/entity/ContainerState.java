package tech.nomad4.backupmanager.isolate.discovery.entity;

/**
 * Represents the runtime state of a Docker container.
 * <p>
 * Maps Docker's internal state strings to a normalized enum for consistent
 * handling throughout the application.
 * </p>
 */
public enum ContainerState {

    RUNNING,
    STOPPED,
    PAUSED,
    RESTARTING,
    DEAD,
    UNKNOWN;

    /**
     * Converts a Docker state string to the corresponding enum value.
     * <p>
     * Handles Docker's state names including "exited" (mapped to {@link #STOPPED}).
     * Returns {@link #UNKNOWN} for unrecognized states.
     * </p>
     *
     * @param dockerState the state string from the Docker API (e.g., "running", "exited")
     * @return the corresponding {@link ContainerState}
     */
    public static ContainerState fromDockerState(String dockerState) {
        if (dockerState == null) {
            return UNKNOWN;
        }
        return switch (dockerState.toLowerCase()) {
            case "running" -> RUNNING;
            case "exited", "stopped" -> STOPPED;
            case "paused" -> PAUSED;
            case "restarting" -> RESTARTING;
            case "dead" -> DEAD;
            default -> UNKNOWN;
        };
    }
}

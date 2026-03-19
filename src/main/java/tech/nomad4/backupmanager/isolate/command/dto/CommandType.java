package tech.nomad4.backupmanager.isolate.command.dto;

/**
 * The type of command to execute inside a database container.
 */
public enum CommandType {

    /** SQL query executed via the database's CLI tool (e.g., psql). */
    SQL,

    /** Shell command such as pg_dump, pg_restore, etc. */
    SHELL,

    /** Raw command passed directly to the container with no wrapping. */
    RAW
}

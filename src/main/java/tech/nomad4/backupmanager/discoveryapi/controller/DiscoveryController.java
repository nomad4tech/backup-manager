package tech.nomad4.backupmanager.discoveryapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.nomad4.backupmanager.isolate.command.exception.CommandExecutionException;
import tech.nomad4.backupmanager.isolate.discovery.entity.ContainerInfo;
import tech.nomad4.backupmanager.isolate.discovery.entity.DatabaseType;
import tech.nomad4.backupmanager.isolate.discovery.exception.ContainerDiscoveryException;
import tech.nomad4.backupmanager.isolate.discovery.service.ContainerDiscoveryService;
import tech.nomad4.backupmanager.discoveryapi.dto.ContainerDetailResponse;
import tech.nomad4.backupmanager.discoveryapi.dto.ContainerSummaryResponse;
import tech.nomad4.backupmanager.discoveryapi.dto.SupportedTypeResponse;
import tech.nomad4.backupmanager.discoveryapi.service.DatabaseListService;
import tech.nomad4.dockersocketmanager.exception.DockerConnectionException;
import tech.nomad4.backupmanager.socketmanagement.exception.SocketNotFoundException;
import tech.nomad4.backupmanager.socketmanagement.service.DockerSocketFacadeService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for discovering database containers on Docker hosts.
 * <p>
 * Stateless - delegates to {@link ContainerDiscoveryService} (core) using a
 * Docker client obtained from {@link DockerSocketFacadeService}. Has no own
 * database or persistent state.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
@Tag(name = "Discovery", description = "Discover database containers on Docker hosts")
public class DiscoveryController {

    private final ContainerDiscoveryService discoveryService;
    private final DockerSocketFacadeService socketFacade;
    private final DatabaseListService databaseListService;

    // -------------------------------------------------------------------------
    // Container list
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List database containers on a socket",
            description = "Returns all database containers visible through the given Docker socket. " +
                    "Optionally filter by database type (e.g. POSTGRES, MYSQL)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ContainerSummaryResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Socket not found",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", description = "Cannot connect to Docker host",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "502", description = "Docker API error",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/sockets/{socketId}/containers")
    public ResponseEntity<List<ContainerSummaryResponse>> listContainers(
            @Parameter(description = "Socket ID", example = "1")
            @PathVariable Long socketId,
            @Parameter(description = "Filter by database type (optional)", example = "POSTGRES")
            @RequestParam(required = false) String type) {

        var client = socketFacade.getDockerClient(socketId);

        List<ContainerInfo> containers;
        if (type != null && !type.isBlank()) {
            DatabaseType dbType = parseDatabaseType(type);
            containers = discoveryService.findByDatabaseType(client, dbType);
        } else {
            containers = discoveryService.findAllDatabaseContainers(client);
        }

        List<ContainerSummaryResponse> response = containers.stream()
                .map(ContainerSummaryResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Container detail
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get full container info",
            description = "Returns detailed information about a specific container including " +
                    "environment variables (sensitive values are masked)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(schema = @Schema(implementation = ContainerDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Socket or container not found",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", description = "Cannot connect to Docker host",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "502", description = "Docker API error",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/sockets/{socketId}/containers/{containerId}")
    public ResponseEntity<ContainerDetailResponse> getContainer(
            @Parameter(description = "Socket ID", example = "1")
            @PathVariable Long socketId,
            @Parameter(description = "Container ID (short or full)", example = "a1b2c3d4e5f6")
            @PathVariable String containerId) {

        var client = socketFacade.getDockerClient(socketId);
        ContainerInfo info = discoveryService.getContainerInfo(client, containerId);
        return ResponseEntity.ok(ContainerDetailResponse.from(info));
    }

    // -------------------------------------------------------------------------
    // Database list
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List databases inside a container",
            description = "Executes a database-type-specific query inside the running container " +
                    "and returns the names of all available (non-template) databases. " +
                    "Currently supported: POSTGRES."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    array = @ArraySchema(schema = @Schema(type = "string", example = "mydb")))),
            @ApiResponse(responseCode = "400", description = "Database type does not support listing",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "Socket or container not found",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "502", description = "In-container query failed",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", description = "Cannot connect to Docker host",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/sockets/{socketId}/containers/{containerId}/databases")
    public ResponseEntity<List<String>> listDatabases(
            @Parameter(description = "Socket ID", example = "1")
            @PathVariable Long socketId,
            @Parameter(description = "Container ID (short or full)", example = "a1b2c3d4e5f6")
            @PathVariable String containerId) {

        var client = socketFacade.getDockerClient(socketId);
        ContainerInfo info = discoveryService.getContainerInfo(client, containerId);
        List<String> databases = databaseListService.listDatabases(client, containerId, info.getDatabaseType());
        return ResponseEntity.ok(databases);
    }

    // -------------------------------------------------------------------------
    // Database size
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get database size",
            description = "Queries the on-disk size of a specific database inside the container. " +
                    "Returns size in bytes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(schema = @Schema(type = "integer", format = "int64"))),
            @ApiResponse(responseCode = "404", description = "Socket or container not found",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "502", description = "In-container query failed",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", description = "Cannot connect to Docker host",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/sockets/{socketId}/containers/{containerId}/databases/{databaseName}/size")
    public ResponseEntity<Long> getDatabaseSize(
            @Parameter(description = "Socket ID", example = "1")
            @PathVariable Long socketId,
            @Parameter(description = "Container ID (short or full)", example = "a1b2c3d4e5f6")
            @PathVariable String containerId,
            @Parameter(description = "Database name", example = "mydb")
            @PathVariable String databaseName) {

        var client = socketFacade.getDockerClient(socketId);
        ContainerInfo info = discoveryService.getContainerInfo(client, containerId);
        long size = databaseListService.getDatabaseSize(
                client, containerId, info.getDatabaseType(), databaseName);
        return ResponseEntity.ok(size);
    }

    // -------------------------------------------------------------------------
    // Supported types
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List supported database types",
            description = "Returns all database types that the discovery service can detect. " +
                    "Use these values in the ?type= filter parameter."
    )
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = SupportedTypeResponse.class))))
    @GetMapping("/types")
    public ResponseEntity<List<SupportedTypeResponse>> getSupportedTypes() {
        List<SupportedTypeResponse> types = Arrays.stream(DatabaseType.values())
                .filter(t -> t != DatabaseType.UNKNOWN)
                .map(SupportedTypeResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }

    // -------------------------------------------------------------------------
    // Exception handlers
    // -------------------------------------------------------------------------

    @ExceptionHandler(SocketNotFoundException.class)
    public ResponseEntity<String> handleSocketNotFound(SocketNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(DockerConnectionException.class)
    public ResponseEntity<String> handleConnectionError(DockerConnectionException e) {
        log.error("Docker connection error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(ContainerDiscoveryException.class)
    public ResponseEntity<String> handleDiscoveryError(ContainerDiscoveryException e) {
        log.error("Container discovery error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }

    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<String> handleCommandError(CommandExecutionException e) {
        log.error("In-container command error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DatabaseType parseDatabaseType(String type) {
        try {
            return DatabaseType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            List<String> valid = Arrays.stream(DatabaseType.values())
                    .filter(t -> t != DatabaseType.UNKNOWN)
                    .map(DatabaseType::name)
                    .collect(Collectors.toList());
            throw new IllegalArgumentException(
                    "Unknown database type: '" + type + "'. Valid values: " + valid
            );
        }
    }
}

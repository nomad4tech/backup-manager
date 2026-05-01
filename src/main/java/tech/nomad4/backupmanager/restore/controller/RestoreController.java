package tech.nomad4.backupmanager.restore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.nomad4.backupmanager.backuphistory.dto.BackupRecordResponse;
import tech.nomad4.backupmanager.discoveryapi.dto.ContainerSummaryResponse;
import tech.nomad4.backupmanager.restore.dto.CreateRestoreRequest;
import tech.nomad4.backupmanager.restore.dto.DbExistsResponse;
import tech.nomad4.backupmanager.restore.dto.RestoreRecordResponse;
import tech.nomad4.backupmanager.restore.service.RestoreService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for triggering and monitoring database restore operations.
 */
@RestController
@RequestMapping("/api/restore")
@RequiredArgsConstructor
@Tag(name = "Restore", description = "Restore a database from a backup file")
public class RestoreController {

    private final RestoreService restoreService;

    @Operation(
            summary = "List restore records",
            description = "Returns a paginated list of restore execution records, newest first."
    )
    @ApiResponse(responseCode = "200", description = "Page of restore records")
    @GetMapping
    public ResponseEntity<Page<RestoreRecordResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                restoreService.findAll(page, size).map(RestoreRecordResponse::from)
        );
    }

    @Operation(summary = "Get restore record by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(schema = @Schema(implementation = RestoreRecordResponse.class))),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<RestoreRecordResponse> get(
            @Parameter(description = "Restore record ID") @PathVariable Long id) {
        return ResponseEntity.ok(RestoreRecordResponse.from(restoreService.findById(id)));
    }

    @Operation(
            summary = "List available backups",
            description = "Returns backup records that have a file on disk and can be used as a restore source."
    )
    @ApiResponse(responseCode = "200", description = "List of backup records with files on disk",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BackupRecordResponse.class))))
    @GetMapping("/backups")
    public ResponseEntity<List<BackupRecordResponse>> availableBackups() {
        return ResponseEntity.ok(
                restoreService.findAvailableBackups().stream()
                        .map(BackupRecordResponse::from)
                        .collect(Collectors.toList())
        );
    }

    @Operation(
            summary = "List compatible containers",
            description = "Returns containers of the given database type reachable via the given socket."
    )
    @ApiResponse(responseCode = "200", description = "List of database containers",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContainerSummaryResponse.class))))
    @GetMapping("/containers")
    public ResponseEntity<List<ContainerSummaryResponse>> containers(
            @Parameter(description = "Docker socket ID") @RequestParam Long socketId,
            @Parameter(description = "Database type (POSTGRES, MYSQL, MARIADB)") @RequestParam String dbType) {
        return ResponseEntity.ok(
                restoreService.findCompatibleContainers(socketId, dbType).stream()
                        .map(ContainerSummaryResponse::from)
                        .collect(Collectors.toList())
        );
    }

    @Operation(
            summary = "Check if database exists",
            description = "Runs a quick exec inside the container to check whether the named database already exists."
    )
    @ApiResponse(responseCode = "200", description = "Database existence check result",
            content = @Content(schema = @Schema(implementation = DbExistsResponse.class)))
    @GetMapping("/check-db")
    public ResponseEntity<DbExistsResponse> checkDatabase(
            @Parameter(description = "Docker socket ID") @RequestParam Long socketId,
            @Parameter(description = "Target container ID") @RequestParam String containerId,
            @Parameter(description = "Database name to check") @RequestParam String dbName,
            @Parameter(description = "Database type (POSTGRES, MYSQL, MARIADB)") @RequestParam String dbType) {
        return ResponseEntity.ok(
                restoreService.checkDatabaseExists(socketId, containerId, dbName, dbType)
        );
    }

    @Operation(
            summary = "Start a restore",
            description = "Creates a restore record with PENDING status and runs the restore asynchronously. " +
                    "Poll GET /api/restore/{id} to track progress."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    content = @Content(schema = @Schema(implementation = RestoreRecordResponse.class))),
            @ApiResponse(responseCode = "400",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PostMapping
    public ResponseEntity<RestoreRecordResponse> create(@Valid @RequestBody CreateRestoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RestoreRecordResponse.from(restoreService.createAndRun(request)));
    }

    @Operation(
            summary = "Cancel a running restore",
            description = "Transitions a RUNNING restore to CANCELLED. Has no effect on finished restores."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @Parameter(description = "Restore record ID") @PathVariable Long id) {
        restoreService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(msg);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
    }
}

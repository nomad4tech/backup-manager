package tech.nomad4.backupmanager.backuptask.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.nomad4.backupmanager.backuptask.dto.BackupTaskRequest;
import tech.nomad4.backupmanager.backuptask.dto.BackupTaskResponse;
import tech.nomad4.backupmanager.backuptask.service.BackupTaskService;
import tech.nomad4.backupmanager.socketmanagement.exception.SocketNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing backup task configurations.
 * <p>
 * Force-run endpoint is located at {@code POST /api/scheduler/tasks/{id}/run}.
 * </p>
 */
@RestController
@RequestMapping("/api/backup-tasks")
@RequiredArgsConstructor
@Tag(name = "Backup Tasks", description = "Manage scheduled backup task configurations")
public class BackupTaskController {

    private final BackupTaskService service;

    @Operation(summary = "List all backup tasks")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = BackupTaskResponse.class))))
    @GetMapping
    public ResponseEntity<List<BackupTaskResponse>> list() {
        return ResponseEntity.ok(
                service.findAll().stream()
                        .map(BackupTaskResponse::from)
                        .collect(Collectors.toList())
        );
    }

    @Operation(summary = "Get backup task by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(schema = @Schema(implementation = BackupTaskResponse.class))),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<BackupTaskResponse> get(
            @Parameter(description = "Task ID") @PathVariable Long id) {
        return ResponseEntity.ok(BackupTaskResponse.from(service.findById(id)));
    }

    @Operation(
            summary = "Create a backup task",
            description = "Validates the socket connection and container existence before creating the task. " +
                    "If enabled=true, the task is scheduled immediately."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    content = @Content(schema = @Schema(implementation = BackupTaskResponse.class))),
            @ApiResponse(responseCode = "400",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "Socket not found",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PostMapping
    public ResponseEntity<BackupTaskResponse> create(@Valid @RequestBody BackupTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BackupTaskResponse.from(service.create(request)));
    }

    @Operation(
            summary = "Update a backup task",
            description = "Re-validates socket and container. The active schedule is automatically updated."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(schema = @Schema(implementation = BackupTaskResponse.class))),
            @ApiResponse(responseCode = "400",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<BackupTaskResponse> update(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @Valid @RequestBody BackupTaskRequest request) {
        return ResponseEntity.ok(BackupTaskResponse.from(service.update(id, request)));
    }

    @Operation(
            summary = "Toggle task enabled state",
            description = "Enables a disabled task or disables an active one. " +
                    "The schedule is started or cancelled accordingly."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(schema = @Schema(implementation = BackupTaskResponse.class))),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<BackupTaskResponse> toggle(
            @Parameter(description = "Task ID") @PathVariable Long id) {
        return ResponseEntity.ok(BackupTaskResponse.from(service.toggle(id)));
    }

    @Operation(
            summary = "Delete a backup task",
            description = "Cancels the schedule and deletes the task. " +
                    "Backup history records are preserved (with taskId set to null)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Task ID") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        // Distinguish 404 (task not found) from 400 (validation)
        String msg = e.getMessage();
        if (msg != null && msg.contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(msg);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
    }

    @ExceptionHandler(SocketNotFoundException.class)
    public ResponseEntity<String> handleSocketNotFound(SocketNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}

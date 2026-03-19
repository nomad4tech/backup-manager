package tech.nomad4.backupmanager.scheduler.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.nomad4.backupmanager.backuptask.service.BackupTaskService;
import tech.nomad4.backupmanager.scheduler.exception.BackupAlreadyRunningException;
import tech.nomad4.backupmanager.scheduler.service.BackupSchedulerService;

/**
 * REST controller for scheduler operations.
 * <p>
 * The force-run endpoint lives here rather than in the backup-task package
 * because triggering execution is a scheduling concern, not a configuration one.
 * </p>
 */
@RestController
@RequestMapping("/api/scheduler/tasks")
@RequiredArgsConstructor
@Tag(name = "Scheduler", description = "Force-run backup tasks outside their normal schedule")
public class BackupSchedulerController {

    private final BackupTaskService taskService;
    private final BackupSchedulerService schedulerService;

    @Operation(
            summary = "Force-run a backup task",
            description = "Immediately submits the task for execution regardless of its configured " +
                    "schedule. Returns 202 Accepted once the run has been queued."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Backup run submitted",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "409", description = "Task is already running",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", description = "No backup slot available",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PostMapping("/{id}/run")
    public ResponseEntity<String> forceRun(
            @Parameter(description = "Task ID") @PathVariable Long id) {
        taskService.findById(id); // throws IllegalArgumentException("not found") if absent
        schedulerService.forceRun(id);
        return ResponseEntity.accepted().body("Backup run submitted for task " + id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(BackupAlreadyRunningException.class)
    public ResponseEntity<String> handleAlreadyRunning(BackupAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleBusy(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}

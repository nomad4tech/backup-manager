package tech.nomad4.backupmanager.backuphistory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.nomad4.backupmanager.backuphistory.dto.BackupRecordResponse;
import tech.nomad4.backupmanager.backuphistory.entity.BackupStatus;
import tech.nomad4.backupmanager.backuphistory.service.BackupRecordService;
import tech.nomad4.backupmanager.scheduler.config.BackupProperties;

import java.time.LocalDateTime;

/**
 * REST controller for viewing and managing backup execution history.
 */
@RestController
@RequestMapping("/api/backup-history")
@RequiredArgsConstructor
@Tag(name = "Backup History", description = "View and manage backup execution records")
public class BackupRecordController {

    private final BackupRecordService service;
    private final BackupProperties backupProperties;

    @Operation(
            summary = "List backup records",
            description = "Returns a paginated list of backup execution records. " +
                    "Supports filtering by task ID, status, and date range."
    )
    @ApiResponse(responseCode = "200", description = "Page of backup records")
    @GetMapping
    public ResponseEntity<Page<BackupRecordResponse>> list(
            @Parameter(description = "Filter by task ID") @RequestParam(required = false) Long taskId,
            @Parameter(description = "Filter by status (RUNNING, SUCCESS, FAILED)")
            @RequestParam(required = false) BackupStatus status,
            @Parameter(description = "Filter: records started on or after this date-time (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Filter: records started before this date-time (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String baseDir = backupProperties.getBaseDirectory();
        return ResponseEntity.ok(
                service.findAll(taskId, status, from, to, page, size)
                        .map(record -> BackupRecordResponse.from(record, baseDir))
        );
    }

    @Operation(summary = "Get backup record by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(schema = @Schema(implementation = BackupRecordResponse.class))),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<BackupRecordResponse> get(
            @Parameter(description = "Backup record ID") @PathVariable Long id) {
        return ResponseEntity.ok(BackupRecordResponse.from(service.findById(id), backupProperties.getBaseDirectory()));
    }

    @Operation(
            summary = "Delete backup record",
            description = "Deletes the backup record and the associated file on disk (if it still exists)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "404",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Backup record ID") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}

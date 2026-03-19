package tech.nomad4.backupmanager.socketmanagement.controller;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.nomad4.backupmanager.isolate.socket.exception.DockerConnectionException;
import tech.nomad4.backupmanager.socketmanagement.dto.ConnectionTestResponse;
import tech.nomad4.backupmanager.socketmanagement.dto.DockerSocketCreateRequest;
import tech.nomad4.backupmanager.socketmanagement.dto.DockerSocketResponse;
import tech.nomad4.backupmanager.socketmanagement.exception.SocketNotFoundException;
import tech.nomad4.backupmanager.socketmanagement.service.DockerSocketFacadeService;

import java.util.List;

/**
 * REST controller for managing Docker socket connections.
 * <p>
 * Provides CRUD operations for Docker socket configurations and connection
 * lifecycle management. Delegates all business logic to {@link DockerSocketFacadeService}.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/sockets")
@RequiredArgsConstructor
@Tag(name = "Docker Sockets", description = "Manage Docker socket connections via SSH tunneling")
public class DockerSocketController {

    private final DockerSocketFacadeService facadeService;

    @Operation(summary = "List all Docker sockets")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = DockerSocketResponse.class))))
    @GetMapping
    public ResponseEntity<List<DockerSocketResponse>> getAllSockets() {
        return ResponseEntity.ok(facadeService.getAllSockets());
    }

    @Operation(summary = "Get Docker socket by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DockerSocketResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<DockerSocketResponse> getSocket(
            @Parameter(description = "Docker socket ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(facadeService.getSocket(id));
    }

    @Operation(summary = "Create Docker socket connection")
    @ApiResponses({
            @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = DockerSocketResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PostMapping
    public ResponseEntity<DockerSocketResponse> createSocket(@Valid @RequestBody DockerSocketCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facadeService.createSocket(request));
    }

    @Operation(summary = "Update Docker socket connection")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DockerSocketResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<DockerSocketResponse> updateSocket(
            @Parameter(description = "Docker socket ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody DockerSocketCreateRequest request) {
        return ResponseEntity.ok(facadeService.updateSocket(id, request));
    }

    @Operation(summary = "Delete Docker socket connection")
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = String.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSocket(
            @Parameter(description = "Docker socket ID", example = "1") @PathVariable Long id) {
        facadeService.deleteSocket(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Connect to Docker host")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ConnectionTestResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = ConnectionTestResponse.class)))
    })
    @PostMapping("/{id}/connect")
    public ResponseEntity<ConnectionTestResponse> connect(
            @Parameter(description = "Docker socket ID", example = "1") @PathVariable Long id) {
        try {
            return ResponseEntity.ok(facadeService.connect(id));
        } catch (DockerConnectionException e) {
            log.error("Connection failed for socket {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ConnectionTestResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Disconnect from Docker host")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PostMapping("/{id}/disconnect")
    public ResponseEntity<Void> disconnect(
            @Parameter(description = "Docker socket ID", example = "1") @PathVariable Long id) {
        facadeService.disconnect(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Test Docker connection")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ConnectionTestResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = ConnectionTestResponse.class)))
    })
    @GetMapping("/{id}/test")
    public ResponseEntity<ConnectionTestResponse> test(
            @Parameter(description = "Docker socket ID", example = "1") @PathVariable Long id) {
        try {
            return ResponseEntity.ok(facadeService.testConnection(id));
        } catch (Exception e) {
            log.error("Connection test failed for socket {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ConnectionTestResponse.error(e.getMessage()));
        }
    }

    @ExceptionHandler(SocketNotFoundException.class)
    public ResponseEntity<String> handleSocketNotFound(SocketNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(DockerConnectionException.class)
    public ResponseEntity<String> handleConnectionException(DockerConnectionException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}

package tech.nomad4.backupmanager.isolate.command.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.command.dto.CommandResult;
import tech.nomad4.backupmanager.isolate.command.dto.ExecutionState;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes commands inside Docker containers via the exec API.
 * <p>
 * Stateless component that takes a {@link DockerClient} as a parameter,
 * keeping it decoupled from connection management. Handles stream capture,
 * timeout enforcement, and exit-code inspection.
 * </p>
 */
@Slf4j
public class DockerExecExecutor {

    /**
     * Executes a command inside a Docker container.
     *
     * @param client         the Docker client to use
     * @param containerId    the target container ID (short or long form)
     * @param command        the command and arguments to execute
     * @param timeoutSeconds maximum time to wait for completion
     * @param envVars        optional environment variables (may be {@code null})
     * @return the captured result including stdout, stderr, and exit code
     */
    public CommandResult execute(DockerClient client,
                                 String containerId,
                                 String[] command,
                                 long timeoutSeconds,
                                 Map<String, String> envVars) {

        LocalDateTime startTime = LocalDateTime.now();
        log.debug("Executing in container {}: {}", containerId, String.join(" ", command));

        try {
            var execCreate = client.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command);

            if (envVars != null && !envVars.isEmpty()) {
                String[] env = envVars.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toArray(String[]::new);
                execCreate.withEnv(java.util.Arrays.asList(env));
            }

            ExecCreateCmdResponse execCreated = execCreate.exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            boolean completed = client.execStartCmd(execCreated.getId())
                    .withDetach(false)
                    .withTty(false)
                    .exec(new ExecStartResultCallback(stdout, stderr))
                    .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);

            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            if (!completed) {
                log.warn("Command timed out after {}s in container {}", timeoutSeconds, containerId);
                return CommandResult.builder()
                        .exitCode(-1)
                        .stdout(stdout.toString())
                        .stderr(stderr.toString())
                        .state(ExecutionState.TIMEOUT)
                        .startedAt(startTime)
                        .completedAt(endTime)
                        .executionTimeMs(durationMs)
                        .build();
            }

            InspectExecResponse inspectExec = client.inspectExecCmd(execCreated.getId()).exec();
            Long exitCodeLong = inspectExec.getExitCodeLong();
            int exitCode = (exitCodeLong != null) ? exitCodeLong.intValue() : -1;

            ExecutionState state = (exitCode == 0) ? ExecutionState.COMPLETED : ExecutionState.FAILED;

            log.debug("Command finished in {}ms with exit code {} in container {}",
                    durationMs, exitCode, containerId);

            return CommandResult.builder()
                    .exitCode(exitCode)
                    .stdout(stdout.toString())
                    .stderr(stderr.toString())
                    .state(state)
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .executionTimeMs(durationMs)
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Command interrupted in container {}", containerId);
            return buildErrorResult(startTime, "Command interrupted: " + e.getMessage());

        } catch (Exception e) {
            log.error("Docker exec failed in container {}: {}", containerId, e.getMessage());
            return buildErrorResult(startTime, e.getMessage());
        }
    }

    private CommandResult buildErrorResult(LocalDateTime startTime, String errorMessage) {
        LocalDateTime endTime = LocalDateTime.now();
        return CommandResult.builder()
                .exitCode(-1)
                .stdout("")
                .stderr(errorMessage)
                .state(ExecutionState.FAILED)
                .startedAt(startTime)
                .completedAt(endTime)
                .executionTimeMs(Duration.between(startTime, endTime).toMillis())
                .build();
    }
}

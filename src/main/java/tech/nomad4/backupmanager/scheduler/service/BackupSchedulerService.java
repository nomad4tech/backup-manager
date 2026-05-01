package tech.nomad4.backupmanager.scheduler.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.backuphistory.entity.BackupStatus;
import tech.nomad4.backupmanager.backuphistory.repository.BackupRecordRepository;
import tech.nomad4.backupmanager.backuptask.entity.BackupTask;
import tech.nomad4.backupmanager.backuptask.entity.ScheduleType;
import tech.nomad4.backupmanager.backuptask.event.BackupTaskCreatedEvent;
import tech.nomad4.backupmanager.backuptask.event.BackupTaskDeletedEvent;
import tech.nomad4.backupmanager.backuptask.event.BackupTaskUpdatedEvent;
import tech.nomad4.backupmanager.backuptask.repository.BackupTaskRepository;
import tech.nomad4.backupmanager.scheduler.exception.BackupAlreadyRunningException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;

/**
 * Dynamic backup scheduler.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Schedule all enabled tasks on application startup.</li>
 *   <li>React to {@link BackupTaskCreatedEvent}, {@link BackupTaskUpdatedEvent}, and
 *       {@link BackupTaskDeletedEvent} to keep the live schedule in sync with the
 *       database without polling.</li>
 *   <li>Enforce the global concurrency limit via a {@link Semaphore}.</li>
 *   <li>Provide a {@link #forceRun(Long)} method for the REST force-run endpoint.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupSchedulerService {

    private final TaskScheduler backupTaskScheduler;
    private final Semaphore backupSemaphore;
    private final BackupTaskRepository taskRepository;
    private final BackupRecordRepository recordRepository;
    private final BackupExecutionOrchestrator orchestrator;

    /** Active scheduled futures keyed by task ID. */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Startup
    // -------------------------------------------------------------------------

    @PostConstruct
    public void scheduleEnabledTasks() {
        List<BackupTask> tasks = taskRepository.findByEnabledTrue();

        log.info("Scheduling {} enabled backup task(s) on startup", tasks.size());
        tasks.forEach(this::scheduleTask);
    }

    // -------------------------------------------------------------------------
    // Spring Event listeners
    // -------------------------------------------------------------------------

    @EventListener
    public void onTaskCreated(BackupTaskCreatedEvent event) {
        scheduleTask(event.getTask());
    }

    /**
     * Cancels the previous schedule and re-schedules the task with the new
     * configuration. If the task was disabled, only the cancellation is performed.
     */
    @EventListener
    public void onTaskUpdated(BackupTaskUpdatedEvent event) {
        BackupTask task = event.getTask();
        cancelTask(task.getId());
        if (Boolean.TRUE.equals(task.getEnabled())) {
            scheduleTask(task);
        }
    }

    @EventListener
    public void onTaskDeleted(BackupTaskDeletedEvent event) {
        cancelTask(event.getTaskId());
    }

    // -------------------------------------------------------------------------
    // Force-run
    // -------------------------------------------------------------------------

    /**
     * Submits the given task for immediate execution outside its normal schedule.
     *
     * @param taskId ID of the task to run
     * @throws BackupAlreadyRunningException if a RUNNING record already exists for the task
     * @throws IllegalStateException         if the global concurrency limit is saturated
     */
    public void forceRun(Long taskId) {
        if (recordRepository.existsByTaskIdAndStatus(taskId, BackupStatus.RUNNING)) {
            throw new BackupAlreadyRunningException(taskId);
        }
        if (!backupSemaphore.tryAcquire()) {
            throw new IllegalStateException(
                    "All backup slots are occupied; try again when a running backup completes");
        }
        // Semaphore is already held - schedule a job that does NOT re-acquire it.
        backupTaskScheduler.schedule(buildPreAcquiredJob(taskId), Instant.now());
        log.info("Force-run submitted for task {}", taskId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void scheduleTask(BackupTask task) {
        Runnable job = buildJob(task.getId(), task.getName());
        ScheduledFuture<?> future;

        if (task.getScheduleType() == ScheduleType.CRON) {
            future = backupTaskScheduler.schedule(job, new CronTrigger(task.getCronExpression()));
            task.setNextScheduledAt(
                    CronExpression.parse(task.getCronExpression()).next(ZonedDateTime.now()).toLocalDateTime());
        } else {
            if (task.getDelaySeconds() == null || task.getDelaySeconds() <= 0) {
                log.warn("Task {} ({}) has invalid delaySeconds={}, skipping DELAY schedule",
                        task.getId(), task.getName(), task.getDelaySeconds());
                return;
            }

            Duration delay = Duration.ofSeconds(task.getDelaySeconds());
            future = backupTaskScheduler.scheduleWithFixedDelay(
                    job,
                    Instant.now().plus(delay),
                    delay
            );
            task.setNextScheduledAt(LocalDateTime.now().plusSeconds(task.getDelaySeconds()));
        }

        taskRepository.save(task);
        scheduledTasks.put(task.getId(), future);
        log.info("Scheduled task {} ({}) [{}]", task.getId(), task.getName(), task.getScheduleType());
    }

    private void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false); // do not interrupt a running execution
            log.info("Cancelled schedule for task {}", taskId);
        }
    }

    /**
     * Builds a job that tries to acquire the semaphore before running.
     * Used for scheduled (cron / fixed-delay) triggers.
     */
    private Runnable buildJob(Long taskId, String taskLabel) {
        return () -> {
            if (!backupSemaphore.tryAcquire()) {
                log.warn("Concurrency limit reached, skipping scheduled run for task {} ({})",
                        taskId, taskLabel);
                return;
            }
            try {
                orchestrator.executeBackup(taskId);
                taskRepository.findById(taskId).ifPresent(task -> {
                    if (task.getScheduleType() == ScheduleType.CRON) {
                        task.setNextScheduledAt(
                                CronExpression.parse(task.getCronExpression()).next(ZonedDateTime.now()).toLocalDateTime());
                    } else {
                        task.setNextScheduledAt(LocalDateTime.now().plusSeconds(task.getDelaySeconds()));
                    }
                    taskRepository.save(task);
                });
            } catch (BackupAlreadyRunningException e) {
                log.warn("Skipping run for task {} ({}): already running", taskId, taskLabel);
            } catch (Exception e) {
                log.error("Backup run failed for task {} ({}): {}",
                        taskId, taskLabel, e.getMessage(), e);
            } finally {
                backupSemaphore.release();
            }
        };
    }

    /**
     * Builds a job that assumes the semaphore is already held by the caller.
     * Used for force-run submissions where {@link #forceRun} pre-acquires the slot.
     */
    private Runnable buildPreAcquiredJob(Long taskId) {
        return () -> {
            try {
                orchestrator.executeBackup(taskId);
            } catch (BackupAlreadyRunningException e) {
                log.warn("Force-run skipped for task {}: already running", taskId);
            } catch (Exception e) {
                log.error("Force-run failed for task {}: {}", taskId, e.getMessage(), e);
            } finally {
                backupSemaphore.release();
            }
        };
    }
}

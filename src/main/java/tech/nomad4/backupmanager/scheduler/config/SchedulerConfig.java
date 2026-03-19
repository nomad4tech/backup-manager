package tech.nomad4.backupmanager.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Semaphore;

/**
 * Spring beans for dynamic task scheduling and concurrency control.
 */
@Configuration
public class SchedulerConfig {

    /**
     * Dedicated {@link TaskScheduler} for backup jobs.
     * Thread-pool size matches {@code maxConcurrent + 1} to allow one extra
     * thread for scheduling overhead.
     */
    @Bean
    public TaskScheduler backupTaskScheduler(BackupProperties props) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(props.getMaxConcurrent() + 1);
        scheduler.setThreadNamePrefix("backup-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Semaphore that limits concurrent backup executions.
     * Initialized from {@link BackupProperties#getMaxConcurrent()}.
     */
    @Bean
    public Semaphore backupSemaphore(BackupProperties props) {
        return new Semaphore(props.getMaxConcurrent());
    }
}

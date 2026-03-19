package tech.nomad4.backupmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Backup Manager application.
 * <p>
 * A Spring Boot application that manages Docker socket connections,
 * supporting both local Unix sockets and remote SSH-tunneled connections
 * with automatic socat relay management.
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class BackupManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackupManagerApplication.class, args);
    }
}

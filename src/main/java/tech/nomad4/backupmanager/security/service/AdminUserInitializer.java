package tech.nomad4.backupmanager.security.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tech.nomad4.backupmanager.security.entity.AdminUser;
import tech.nomad4.backupmanager.security.repository.AdminUserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer {

    private final AdminUserRepository repo;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        if (repo.count() == 0) {
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("admin"));
            repo.save(admin);
            log.info("Created default admin user (username: admin, password: admin) - change this immediately.");
        }
    }
}

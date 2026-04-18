package tech.nomad4.backupmanager.security.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.nomad4.backupmanager.security.entity.AdminUser;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);
}

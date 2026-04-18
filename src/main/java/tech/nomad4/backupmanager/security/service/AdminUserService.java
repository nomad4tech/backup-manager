package tech.nomad4.backupmanager.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tech.nomad4.backupmanager.security.dto.ChangeCredentialsRequest;
import tech.nomad4.backupmanager.security.entity.AdminUser;
import tech.nomad4.backupmanager.security.repository.AdminUserRepository;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository repo;
    private final PasswordEncoder passwordEncoder;

    public void changeCredentials(String currentUsername, ChangeCredentialsRequest request) {
        AdminUser user = repo.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (request.getNewUsername() != null && !request.getNewUsername().isBlank()) {
            user.setUsername(request.getNewUsername().strip());
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        repo.save(user);
    }
}

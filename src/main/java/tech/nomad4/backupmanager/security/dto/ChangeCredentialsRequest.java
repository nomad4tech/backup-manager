package tech.nomad4.backupmanager.security.dto;

import lombok.Data;

@Data
public class ChangeCredentialsRequest {
    private String currentPassword;
    private String newUsername;
    private String newPassword;
}

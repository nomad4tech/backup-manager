package tech.nomad4.backupmanager.appsettings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionCheckResult {
    private boolean reachable;
    private String errorMessage;
}

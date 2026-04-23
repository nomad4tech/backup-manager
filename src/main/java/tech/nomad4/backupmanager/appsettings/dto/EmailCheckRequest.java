package tech.nomad4.backupmanager.appsettings.dto;

import lombok.Data;

@Data
public class EmailCheckRequest {
    private String host;
    private Integer port;
    private String username;
    /** null = use stored password from DB */
    private String password;
    private String from;
    private boolean ssl;
    private boolean startTls;
    private Integer timeoutMs;
}

package tech.nomad4.backupmanager.appsettings.dto;

import lombok.Data;

@Data
public class AwsCheckRequest {
    private String bucketName;
    private String region;
    private String accessKey;
    /** null means: use the stored secret key from DB */
    private String awsSecretKey;
    private String endpoint;
    private boolean pathStyleAccess;
    private String destinationDirectory;
}

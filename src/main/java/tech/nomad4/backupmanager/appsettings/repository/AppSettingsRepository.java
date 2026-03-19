package tech.nomad4.backupmanager.appsettings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.nomad4.backupmanager.appsettings.entity.AppSettings;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
}

package tech.nomad4.backupmanager.restore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.nomad4.backupmanager.restore.entity.RestoreRecord;
import tech.nomad4.backupmanager.restore.entity.RestoreStatus;

import java.util.List;

@Repository
public interface RestoreRecordRepository extends JpaRepository<RestoreRecord, Long> {

    Page<RestoreRecord> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<RestoreRecord> findByStatusIn(List<RestoreStatus> statuses);

    boolean existsByBackupRecordIdAndStatusIn(Long backupRecordId, List<RestoreStatus> statuses);
}

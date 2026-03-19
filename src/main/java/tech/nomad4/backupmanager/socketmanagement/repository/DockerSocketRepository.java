package tech.nomad4.backupmanager.socketmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.nomad4.backupmanager.socketmanagement.entity.ConnectionStatus;
import tech.nomad4.backupmanager.socketmanagement.entity.DockerSocket;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DockerSocket} entities.
 */
@Repository
public interface DockerSocketRepository extends JpaRepository<DockerSocket, Long> {

    Optional<DockerSocket> findByName(String name);

    Optional<DockerSocket> findByIsSystemTrue();

    List<DockerSocket> findByStatus(ConnectionStatus status);

    boolean existsByName(String name);
}

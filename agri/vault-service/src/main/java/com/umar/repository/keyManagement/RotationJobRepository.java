package com.umar.repository.keyManagement;

import com.umar.entity.keyManagement.RotationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RotationJobRepository extends JpaRepository<RotationJob, UUID> {

    Optional<RotationJob> findFirstByKeyTypeAndScopeAndStatusIn(String keyType, String scope, List<String> statuses);

    List<RotationJob> findByStatus(String status);
}

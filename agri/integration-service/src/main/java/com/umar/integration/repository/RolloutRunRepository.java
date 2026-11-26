package com.umar.integration.repository;

import com.umar.integration.domain.entity.RolloutRunEntity;
import com.umar.integration.domain.enums.RolloutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolloutRunRepository extends JpaRepository<RolloutRunEntity, String> {
    Optional<RolloutRunEntity> findFirstByExtensionIdAndExtensionVersionAndStatus(
            String extensionId, String extensionVersion, RolloutStatus status);
    List<RolloutRunEntity> findByExtensionIdAndExtensionVersionOrderByCreatedAtDesc(
            String extensionId, String extensionVersion);
    List<RolloutRunEntity> findByHookIdAndStatus(String hookId, RolloutStatus status);
}

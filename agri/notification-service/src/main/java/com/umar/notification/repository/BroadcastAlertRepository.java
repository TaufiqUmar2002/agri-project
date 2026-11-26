package com.umar.notification.repository;

import com.umar.notification.domain.BroadcastAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BroadcastAlertRepository extends JpaRepository<BroadcastAlertEntity, UUID> {
    Optional<BroadcastAlertEntity> findByBroadcastId(String broadcastId);
}

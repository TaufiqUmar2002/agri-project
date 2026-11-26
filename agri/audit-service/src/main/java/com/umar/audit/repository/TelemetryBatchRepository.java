package com.umar.audit.repository;

import com.umar.audit.domain.TelemetryBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelemetryBatchRepository extends JpaRepository<TelemetryBatchEntity, UUID> {
    List<TelemetryBatchEntity> findBySealedFalse();
    Optional<TelemetryBatchEntity> findFirstByDeviceIdAndSealedFalseOrderByCreatedAtDesc(String deviceId);
}

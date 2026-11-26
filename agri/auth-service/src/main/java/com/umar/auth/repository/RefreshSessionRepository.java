package com.umar.auth.repository;

import com.umar.auth.domain.entity.RefreshSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSessionEntity, UUID> {
    Optional<RefreshSessionEntity> findByTokenHash(String tokenHash);
    List<RefreshSessionEntity> findBySessionFamilyId(UUID sessionFamilyId);
    List<RefreshSessionEntity> findByUserIdAndStatus(UUID userId, String status);
    boolean existsByUserIdAndDeviceId(UUID userId, String deviceId);
}

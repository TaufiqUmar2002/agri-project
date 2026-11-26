package com.umar.notification.repository;

import com.umar.notification.domain.UssdSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UssdSessionRepository extends JpaRepository<UssdSessionEntity, UUID> {
    Optional<UssdSessionEntity> findBySessionId(String sessionId);
}

package com.umar.auth.repository;

import com.umar.auth.domain.entity.AuthOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthOutboxEventRepository extends JpaRepository<AuthOutboxEventEntity, UUID> {
    List<AuthOutboxEventEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);
    List<AuthOutboxEventEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);
}

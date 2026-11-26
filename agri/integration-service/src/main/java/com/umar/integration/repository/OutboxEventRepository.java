package com.umar.integration.repository;

import com.umar.integration.domain.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {
    List<OutboxEventEntity> findTop100ByPublishedFalseOrderByCreatedAtAsc();
    List<OutboxEventEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);
}

package com.umar.trigger.repository;

import com.umar.trigger.domain.TriggerOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TriggerOutboxEventRepository extends JpaRepository<TriggerOutboxEventEntity, UUID> {
    List<TriggerOutboxEventEntity> findByPublishedFalseOrderByCreatedAtAsc();
}

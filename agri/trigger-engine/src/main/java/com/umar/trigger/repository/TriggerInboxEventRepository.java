package com.umar.trigger.repository;

import com.umar.trigger.domain.TriggerInboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriggerInboxEventRepository extends JpaRepository<TriggerInboxEventEntity, String> {
}

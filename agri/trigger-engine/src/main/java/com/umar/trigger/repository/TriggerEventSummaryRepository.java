package com.umar.trigger.repository;

import com.umar.trigger.domain.TriggerEventSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriggerEventSummaryRepository extends JpaRepository<TriggerEventSummaryEntity, String> {
}

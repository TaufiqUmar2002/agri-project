package com.umar.trigger.repository;

import com.umar.trigger.domain.TriggerIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriggerIdempotencyRepository extends JpaRepository<TriggerIdempotencyEntity, String> {
}

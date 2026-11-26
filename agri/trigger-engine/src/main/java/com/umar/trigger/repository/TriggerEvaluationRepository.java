package com.umar.trigger.repository;

import com.umar.trigger.domain.TriggerEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TriggerEvaluationRepository extends JpaRepository<TriggerEvaluationEntity, String> {
    Optional<TriggerEvaluationEntity> findByRuleIdAndWindowIdAndStatus(String ruleId, String windowId, String status);
}

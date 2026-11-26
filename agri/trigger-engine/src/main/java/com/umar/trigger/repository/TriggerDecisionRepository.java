package com.umar.trigger.repository;

import com.umar.trigger.domain.TriggerDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TriggerDecisionRepository extends JpaRepository<TriggerDecisionEntity, String> {
    List<TriggerDecisionEntity> findByDecisionIdIn(List<String> decisionIds);
    Optional<TriggerDecisionEntity> findByPolicyIdAndWindowStartAndWindowEnd(String policyId, LocalDate windowStart, LocalDate windowEnd);
    Optional<TriggerDecisionEntity> findByPolicyIdAndWindowIdAndRuleId(String policyId, String windowId, String ruleId);
    List<TriggerDecisionEntity> findByEvaluationId(String evaluationId);
}

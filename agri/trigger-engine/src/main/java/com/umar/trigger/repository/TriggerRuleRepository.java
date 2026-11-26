package com.umar.trigger.repository;

import com.umar.enums.trigger.RuleStatus;
import com.umar.trigger.domain.TriggerRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TriggerRuleRepository extends JpaRepository<TriggerRuleEntity, TriggerRuleEntity.RuleVersionId> {
    List<TriggerRuleEntity> findByRuleCodeAndStatusIn(String ruleCode, List<RuleStatus> statuses);
    Optional<TriggerRuleEntity> findFirstByRuleCodeOrderByVersionNumDesc(String ruleCode);
    Optional<TriggerRuleEntity> findFirstByRuleIdOrderByVersionNumDesc(String ruleId);
    Optional<TriggerRuleEntity> findFirstByRuleIdAndStatusOrderByVersionNumDesc(String ruleId, RuleStatus status);
}

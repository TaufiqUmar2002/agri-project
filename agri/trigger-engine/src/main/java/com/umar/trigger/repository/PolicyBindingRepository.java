package com.umar.trigger.repository;

import com.umar.trigger.domain.PolicyBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyBindingRepository extends JpaRepository<PolicyBindingEntity, String> {
    List<PolicyBindingEntity> findByRuleIdAndActiveTrue(String ruleId);
}

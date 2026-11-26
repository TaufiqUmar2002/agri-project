package com.umar.policy.repository;

import com.umar.policy.entity.PolicyHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyHistoryRepository extends JpaRepository<PolicyHistoryEntity, UUID> {
    List<PolicyHistoryEntity> findByPolicyIdOrderByOccurredAtAsc(String policyId);
}

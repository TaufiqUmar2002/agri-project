package com.umar.policy.repository;

import com.umar.policy.entity.PolicyOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyOutboxEventRepository extends JpaRepository<PolicyOutboxEventEntity, UUID> {
    List<PolicyOutboxEventEntity> findByPublishedFalseOrderByCreatedAtAsc();
}

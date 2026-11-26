package com.umar.billing.repository;

import com.umar.billing.entity.BillingOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillingOutboxEventRepository extends JpaRepository<BillingOutboxEventEntity, UUID> {
    List<BillingOutboxEventEntity> findByPublishedFalseOrderByCreatedAtAsc();
}

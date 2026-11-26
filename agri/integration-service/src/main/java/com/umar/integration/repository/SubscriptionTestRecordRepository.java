package com.umar.integration.repository;

import com.umar.integration.domain.entity.SubscriptionTestRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface SubscriptionTestRecordRepository extends JpaRepository<SubscriptionTestRecordEntity, String> {
    long countBySubscriptionIdAndTestedAtAfter(String subscriptionId, Instant since);
}

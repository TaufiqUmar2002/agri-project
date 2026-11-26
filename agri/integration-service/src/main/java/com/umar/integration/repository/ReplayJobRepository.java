package com.umar.integration.repository;

import com.umar.integration.domain.entity.ReplayJobEntity;
import com.umar.integration.domain.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ReplayJobRepository extends JpaRepository<ReplayJobEntity, String> {
    Optional<ReplayJobEntity> findFirstBySubscriptionIdAndStatusIn(String subscriptionId, Collection<JobStatus> statuses);
    long countBySubscriptionIdAndCreatedAtAfter(String subscriptionId, Instant since);
}

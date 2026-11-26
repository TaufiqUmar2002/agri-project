package com.umar.policy.repository;

import com.umar.enums.policy.PerilType;
import com.umar.enums.policy.PolicyStatus;
import com.umar.policy.entity.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<PolicyEntity, String> {
    Optional<PolicyEntity> findByPolicyIdOrPolicyNumber(String policyId, String policyNumber);
    Optional<PolicyEntity> findByQuoteId(String quoteId);
    List<PolicyEntity> findByFarmerId(UUID farmerId);
    List<PolicyEntity> findByPlotId(String plotId);
    List<PolicyEntity> findByPlotIdAndPerilAndStatusIn(String plotId, PerilType peril, Collection<PolicyStatus> statuses);
    List<PolicyEntity> findByStatusInAndCoverageEndBetween(Collection<PolicyStatus> statuses, Instant from, Instant to);
}

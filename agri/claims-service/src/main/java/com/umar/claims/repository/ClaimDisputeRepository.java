package com.umar.claims.repository;

import com.umar.claims.domain.ClaimDisputeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimDisputeRepository extends JpaRepository<ClaimDisputeEntity, String> {
    List<ClaimDisputeEntity> findByClaimIdOrderByCreatedAtDesc(String claimId);
    Optional<ClaimDisputeEntity> findByDisputeIdAndClaimId(String disputeId, String claimId);
}

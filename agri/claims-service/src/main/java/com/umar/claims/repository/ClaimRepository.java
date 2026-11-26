package com.umar.claims.repository;

import com.umar.claims.domain.ClaimEntity;
import com.umar.enums.claims.ClaimStatus;
import com.umar.enums.claims.ClaimType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<ClaimEntity, String> {
    Optional<ClaimEntity> findByPolicyIdAndWindowIdAndTierIdAndType(String policyId, String windowId, String tierId, ClaimType type);
    List<ClaimEntity> findByPolicyIdAndStatusIn(String policyId, List<ClaimStatus> statuses);
    List<ClaimEntity> findByPolicyId(String policyId);
    List<ClaimEntity> findByFarmerId(String farmerId);
    List<ClaimEntity> findByDecisionId(String decisionId);
}

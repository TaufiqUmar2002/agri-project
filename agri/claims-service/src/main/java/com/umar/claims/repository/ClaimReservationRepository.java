package com.umar.claims.repository;

import com.umar.claims.domain.ClaimReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimReservationRepository extends JpaRepository<ClaimReservationEntity, String> {
    Optional<ClaimReservationEntity> findByClaimId(String claimId);
    List<ClaimReservationEntity> findByPolicyId(String policyId);
}

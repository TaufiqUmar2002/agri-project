package com.umar.claims.repository;

import com.umar.claims.domain.ClaimApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimApprovalRepository extends JpaRepository<ClaimApprovalEntity, String> {
    List<ClaimApprovalEntity> findByClaimIdOrderByDecidedAtAsc(String claimId);
}

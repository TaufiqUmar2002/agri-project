package com.umar.payout.repository;

import com.umar.enums.payout.PayoutStatus;
import com.umar.payout.domain.PayoutInstructionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayoutInstructionRepository extends JpaRepository<PayoutInstructionEntity, String> {
    Optional<PayoutInstructionEntity> findByIdempotencyKey(String idempotencyKey);
    Optional<PayoutInstructionEntity> findByProviderRef(String providerRef);
    Optional<PayoutInstructionEntity> findByOriginatorRef(String originatorRef);
    List<PayoutInstructionEntity> findByClaimId(String claimId);
    List<PayoutInstructionEntity> findByStatusIn(List<PayoutStatus> statuses);
    List<PayoutInstructionEntity> findByConnectionAndStatus(String connection, PayoutStatus status);
}

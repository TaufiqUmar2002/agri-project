package com.umar.auth.repository;

import com.umar.auth.domain.entity.KycCaseEntity;
import com.umar.enums.auth.KycCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface KycCaseRepository extends JpaRepository<KycCaseEntity, UUID> {
    List<KycCaseEntity> findByUserIdAndStatusIn(UUID userId, Collection<KycCaseStatus> statuses);
    List<KycCaseEntity> findByIdNumberHashAndUserIdNot(String idNumberHash, UUID userId);
    long countByUserIdAndSubmittedAtAfter(UUID userId, Instant after);
    long countByUserIdAndStatusAndDecidedAtAfter(UUID userId, KycCaseStatus status, Instant after);
    List<KycCaseEntity> findByStatusOrderBySubmittedAtAsc(KycCaseStatus status);
}

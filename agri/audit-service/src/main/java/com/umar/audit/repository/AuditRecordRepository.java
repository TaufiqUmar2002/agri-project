package com.umar.audit.repository;

import com.umar.audit.domain.AuditRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID> {
    Optional<AuditRecordEntity> findFirstByChainIdOrderBySeqDesc(String chainId);
    List<AuditRecordEntity> findByChainIdOrderBySeqAsc(String chainId);
    List<AuditRecordEntity> findByChainIdAndSeqBetweenOrderBySeqAsc(String chainId, long fromSeq, long toSeq);
    List<AuditRecordEntity> findByClaimIdOrderByOccurredAtAsc(String claimId);
    List<AuditRecordEntity> findBySubjectIdOrderByOccurredAtAsc(String subjectId);
    List<AuditRecordEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(Instant from, Instant to);
}

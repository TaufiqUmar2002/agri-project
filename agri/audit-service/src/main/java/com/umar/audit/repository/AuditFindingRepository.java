package com.umar.audit.repository;

import com.umar.audit.domain.AuditFindingEntity;
import com.umar.enums.audit.FindingSeverity;
import com.umar.enums.audit.FindingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditFindingRepository extends JpaRepository<AuditFindingEntity, UUID> {
    Optional<AuditFindingEntity> findByFindingId(String findingId);
    List<AuditFindingEntity> findByClaimIdOrderByCreatedAtDesc(String claimId);
    List<AuditFindingEntity> findByStatusOrderByCreatedAtDesc(FindingStatus status);
    boolean existsByRuleCodeAndStatus(String ruleCode, FindingStatus status);
    boolean existsBySeverityAndStatus(FindingSeverity severity, FindingStatus status);
}

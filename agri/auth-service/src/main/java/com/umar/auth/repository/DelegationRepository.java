package com.umar.auth.repository;

import com.umar.auth.domain.entity.DelegationEntity;
import com.umar.enums.auth.DelegationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface DelegationRepository extends JpaRepository<DelegationEntity, UUID> {
    long countByAgentIdAndCreatedAtAfter(UUID agentId, Instant after);
    long countByAgentIdAndStatusAndConfirmedAtAfter(UUID agentId, DelegationStatus status, Instant after);
}

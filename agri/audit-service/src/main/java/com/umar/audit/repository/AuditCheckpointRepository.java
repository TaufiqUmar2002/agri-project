package com.umar.audit.repository;

import com.umar.audit.domain.AuditCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditCheckpointRepository extends JpaRepository<AuditCheckpointEntity, UUID> {
    List<AuditCheckpointEntity> findByChainIdOrderByToSeqDesc(String chainId);
    Optional<AuditCheckpointEntity> findFirstByChainIdOrderByToSeqDesc(String chainId);
}

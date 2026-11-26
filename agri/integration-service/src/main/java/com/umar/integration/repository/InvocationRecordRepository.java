package com.umar.integration.repository;

import com.umar.integration.domain.entity.InvocationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InvocationRecordRepository extends JpaRepository<InvocationRecordEntity, String> {
    List<InvocationRecordEntity> findByTenantIdAndCreatedAtBetween(String tenantId, Instant from, Instant to);
    List<InvocationRecordEntity> findByExtensionIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String extensionId, Instant from, Instant to);
    List<InvocationRecordEntity> findByCreatedAtBetween(Instant from, Instant to);
}

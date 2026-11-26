package com.umar.billing.repository;

import com.umar.billing.entity.ReconciliationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRunEntity, String> {
    Optional<ReconciliationRunEntity> findBySourceAndStatementDateAndChecksum(
            String source, LocalDate statementDate, String checksum);
}

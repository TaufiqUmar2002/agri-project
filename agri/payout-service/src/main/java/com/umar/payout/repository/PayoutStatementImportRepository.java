package com.umar.payout.repository;

import com.umar.payout.domain.PayoutStatementImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PayoutStatementImportRepository extends JpaRepository<PayoutStatementImportEntity, String> {
    Optional<PayoutStatementImportEntity> findByConnectionAndStatementDate(String connection, LocalDate date);
}

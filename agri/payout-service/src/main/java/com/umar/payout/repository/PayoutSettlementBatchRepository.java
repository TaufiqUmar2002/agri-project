package com.umar.payout.repository;

import com.umar.payout.domain.PayoutSettlementBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PayoutSettlementBatchRepository extends JpaRepository<PayoutSettlementBatchEntity, String> {
    List<PayoutSettlementBatchEntity> findBySettlementDateBetween(LocalDate from, LocalDate to);
}

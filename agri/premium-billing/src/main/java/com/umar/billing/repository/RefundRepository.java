package com.umar.billing.repository;

import com.umar.billing.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<RefundEntity, String> {
    List<RefundEntity> findByInvoiceIdOrderByCreatedAtDesc(String invoiceId);
    Optional<RefundEntity> findBySourceEventId(String sourceEventId);
}

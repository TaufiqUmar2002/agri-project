package com.umar.billing.repository;

import com.umar.billing.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {
    Optional<InvoiceEntity> findByPolicyId(String policyId);
    Optional<InvoiceEntity> findByPaymentReference(String paymentReference);
    Optional<InvoiceEntity> findByInvoiceIdOrInvoiceNumberOrPaymentReference(String id, String number, String ref);
    List<InvoiceEntity> findByBatchId(String batchId);
}

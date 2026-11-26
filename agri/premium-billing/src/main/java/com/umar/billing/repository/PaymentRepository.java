package com.umar.billing.repository;

import com.umar.billing.entity.PaymentEntity;
import com.umar.enums.billing.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    List<PaymentEntity> findByInvoiceIdAndStatusOrderByReceivedAtDesc(String invoiceId, PaymentStatus status);
    List<PaymentEntity> findByInvoiceIdOrderByReceivedAtDesc(String invoiceId);
    Optional<PaymentEntity> findByProviderAndProviderTxnId(String provider, String providerTxnId);
    Optional<PaymentEntity> findByReceiptNumber(String receiptNumber);
    Optional<PaymentEntity> findByStatementLineRef(String statementLineRef);
}

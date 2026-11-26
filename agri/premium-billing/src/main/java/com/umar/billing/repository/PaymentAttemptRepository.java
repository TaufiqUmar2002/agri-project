package com.umar.billing.repository;

import com.umar.billing.entity.PaymentAttemptEntity;
import com.umar.enums.billing.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, String> {
    Optional<PaymentAttemptEntity> findFirstByInvoiceIdAndStatusInAndExpiresAtAfter(
            String invoiceId, Collection<PaymentAttemptStatus> statuses, Instant now);
    List<PaymentAttemptEntity> findByInvoiceIdOrderByCreatedAtDesc(String invoiceId);
}

package com.umar.billing.repository;

import com.umar.billing.entity.InvoiceLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLineEntity, UUID> {
    List<InvoiceLineEntity> findByInvoiceId(String invoiceId);
}

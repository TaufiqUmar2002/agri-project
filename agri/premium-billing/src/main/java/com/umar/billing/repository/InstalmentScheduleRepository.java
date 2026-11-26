package com.umar.billing.repository;

import com.umar.billing.entity.InstalmentScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstalmentScheduleRepository extends JpaRepository<InstalmentScheduleEntity, UUID> {
    List<InstalmentScheduleEntity> findByInvoiceIdOrderBySeqAsc(String invoiceId);
}

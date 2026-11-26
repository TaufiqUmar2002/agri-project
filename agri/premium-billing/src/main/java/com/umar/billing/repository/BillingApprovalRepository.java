package com.umar.billing.repository;

import com.umar.billing.entity.BillingApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingApprovalRepository extends JpaRepository<BillingApprovalEntity, String> {
}

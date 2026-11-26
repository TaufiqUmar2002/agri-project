package com.umar.billing.repository;

import com.umar.billing.entity.BillingIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingIdempotencyRepository extends JpaRepository<BillingIdempotencyEntity, String> {
}

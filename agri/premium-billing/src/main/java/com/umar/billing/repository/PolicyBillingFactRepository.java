package com.umar.billing.repository;

import com.umar.billing.entity.PolicyBillingFactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyBillingFactRepository extends JpaRepository<PolicyBillingFactEntity, String> {
}

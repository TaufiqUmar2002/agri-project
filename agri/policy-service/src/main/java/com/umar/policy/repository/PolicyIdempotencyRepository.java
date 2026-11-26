package com.umar.policy.repository;

import com.umar.policy.entity.PolicyIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyIdempotencyRepository extends JpaRepository<PolicyIdempotencyEntity, String> {
}

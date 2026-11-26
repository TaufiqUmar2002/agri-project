package com.umar.riskpool.repository;

import com.umar.riskpool.domain.RiskPoolIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskPoolIdempotencyRepository extends JpaRepository<RiskPoolIdempotencyEntity, String> {
}

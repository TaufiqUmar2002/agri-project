package com.umar.riskpool.repository;

import com.umar.riskpool.domain.RiskPoolOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RiskPoolOutboxEventRepository extends JpaRepository<RiskPoolOutboxEventEntity, UUID> {
}

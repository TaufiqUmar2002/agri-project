package com.umar.riskpool.repository;

import com.umar.riskpool.domain.RiskPoolInboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskPoolInboxEventRepository extends JpaRepository<RiskPoolInboxEventEntity, String> {
}

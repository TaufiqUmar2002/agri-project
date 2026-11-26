package com.umar.riskpool.repository;

import com.umar.riskpool.domain.RiskPoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskPoolRepository extends JpaRepository<RiskPoolEntity, String> {
    Optional<RiskPoolEntity> findByPoolCode(String poolCode);
}

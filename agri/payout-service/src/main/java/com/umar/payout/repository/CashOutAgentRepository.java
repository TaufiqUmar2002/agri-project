package com.umar.payout.repository;

import com.umar.payout.domain.CashOutAgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashOutAgentRepository extends JpaRepository<CashOutAgentEntity, String> {
    Optional<CashOutAgentEntity> findFirstByAreaCodeAndActiveTrue(String areaCode);
}

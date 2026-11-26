package com.umar.riskpool.repository;

import com.umar.riskpool.domain.PolicyPremiumRegisterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyPremiumRegisterRepository extends JpaRepository<PolicyPremiumRegisterEntity, String> {
    List<PolicyPremiumRegisterEntity> findByPoolId(String poolId);
}

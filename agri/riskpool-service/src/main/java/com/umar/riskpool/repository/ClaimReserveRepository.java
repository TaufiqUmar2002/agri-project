package com.umar.riskpool.repository;

import com.umar.riskpool.domain.ClaimReserveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimReserveRepository extends JpaRepository<ClaimReserveEntity, String> {
    List<ClaimReserveEntity> findByPoolId(String poolId);
}

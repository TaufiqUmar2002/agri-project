package com.umar.riskpool.repository;

import com.umar.riskpool.domain.PoolProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PoolProjectionRepository extends JpaRepository<PoolProjectionEntity, String> {
    List<PoolProjectionEntity> findByPoolId(String poolId);
}

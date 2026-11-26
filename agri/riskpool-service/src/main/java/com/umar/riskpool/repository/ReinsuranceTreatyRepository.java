package com.umar.riskpool.repository;

import com.umar.riskpool.domain.ReinsuranceTreatyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReinsuranceTreatyRepository extends JpaRepository<ReinsuranceTreatyEntity, String> {
    List<ReinsuranceTreatyEntity> findByPoolId(String poolId);
    List<ReinsuranceTreatyEntity> findByPoolIdAndTreatyCodeAndStatus(String poolId, String treatyCode, String status);
}

package com.umar.riskpool.repository;

import com.umar.riskpool.domain.CapacityReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CapacityReservationRepository extends JpaRepository<CapacityReservationEntity, String> {
    Optional<CapacityReservationEntity> findByPolicyId(String policyId);
    List<CapacityReservationEntity> findByPoolIdAndStatus(String poolId, String status);
}

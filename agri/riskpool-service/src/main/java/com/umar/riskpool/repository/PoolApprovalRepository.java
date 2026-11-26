package com.umar.riskpool.repository;

import com.umar.riskpool.domain.PoolApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoolApprovalRepository extends JpaRepository<PoolApprovalEntity, String> {
}

package com.umar.billing.repository;

import com.umar.billing.entity.InstalmentPlanEntity;
import com.umar.enums.billing.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstalmentPlanRepository extends JpaRepository<InstalmentPlanEntity, String> {
    Optional<InstalmentPlanEntity> findFirstByPlanCodeOrderByVersionNumDesc(String planCode);
    Optional<InstalmentPlanEntity> findByPlanCodeAndPlanHash(String planCode, String planHash);
    List<InstalmentPlanEntity> findByPlanCodeAndStatus(String planCode, PlanStatus status);
}

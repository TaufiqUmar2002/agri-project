package com.umar.plot.repository;

import com.umar.plot.domain.entity.PlotValidationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlotValidationRepository extends JpaRepository<PlotValidationEntity, UUID> {
    Optional<PlotValidationEntity> findFirstByPlotIdAndGeometryVersionAndRuleSetVersionAndDryRunFalseOrderByCreatedAtDesc(
            String plotId, int geometryVersion, String ruleSetVersion);
    Optional<PlotValidationEntity> findFirstByPlotIdOrderByCreatedAtDesc(String plotId);
}

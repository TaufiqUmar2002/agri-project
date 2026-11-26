package com.umar.plot.repository;

import com.umar.enums.plot.CoverageStatus;
import com.umar.plot.domain.entity.PlotCoverageWindowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlotCoverageWindowRepository extends JpaRepository<PlotCoverageWindowEntity, UUID> {
    List<PlotCoverageWindowEntity> findByPlotId(String plotId);
    List<PlotCoverageWindowEntity> findByPlotIdAndStatus(String plotId, CoverageStatus status);
}

package com.umar.plot.repository;

import com.umar.enums.plot.PlotStatus;
import com.umar.plot.domain.entity.PlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlotRepository extends JpaRepository<PlotEntity, String> {
    List<PlotEntity> findByFarmerId(UUID farmerId);
    long countByFarmerIdAndStatusNot(UUID farmerId, PlotStatus status);
    List<PlotEntity> findByStatusIn(Collection<PlotStatus> statuses);
    List<PlotEntity> findByStatusOrderByPlotIdAsc(PlotStatus status);
}

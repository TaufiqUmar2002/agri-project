package com.umar.plot.repository;

import com.umar.enums.plot.SeasonStatus;
import com.umar.plot.domain.entity.PlotSeasonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PlotSeasonRepository extends JpaRepository<PlotSeasonEntity, String> {
    Optional<PlotSeasonEntity> findFirstByPlotIdAndStatusInOrderByCreatedAtDesc(String plotId, Collection<SeasonStatus> statuses);
    Optional<PlotSeasonEntity> findFirstByPlotIdOrderByCreatedAtDesc(String plotId);
}

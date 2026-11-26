package com.umar.plot.repository;

import com.umar.plot.domain.entity.PlotOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlotOutboxEventRepository extends JpaRepository<PlotOutboxEventEntity, UUID> {
    List<PlotOutboxEventEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);
}

package com.umar.trigger.repository;

import com.umar.trigger.domain.SimulationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunRepository extends JpaRepository<SimulationRunEntity, String> {
}

package com.umar.trigger.repository;

import com.umar.trigger.domain.ObservationArchiveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ObservationArchiveRepository extends JpaRepository<ObservationArchiveEntity, String> {
    List<ObservationArchiveEntity> findByRegionCodeInAndObservedDateBetween(List<String> regions, LocalDate from, LocalDate to);
}

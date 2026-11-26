package com.umar.audit.repository;

import com.umar.audit.domain.RegulatoryReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryReportRepository extends JpaRepository<RegulatoryReportEntity, UUID> {
    Optional<RegulatoryReportEntity> findByReportId(String reportId);
    List<RegulatoryReportEntity> findByScheduleId(String scheduleId);
}

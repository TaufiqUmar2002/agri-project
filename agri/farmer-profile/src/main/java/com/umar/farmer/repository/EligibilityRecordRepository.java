package com.umar.farmer.repository;

import com.umar.farmer.domain.entity.EligibilityRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EligibilityRecordRepository extends JpaRepository<EligibilityRecordEntity, UUID> {}

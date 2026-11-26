package com.umar.farmer.repository;

import com.umar.enums.farmer.FarmerStatus;
import com.umar.farmer.domain.entity.FarmerProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfileEntity, UUID> {
    Optional<FarmerProfileEntity> findByUserId(UUID userId);
    List<FarmerProfileEntity> findByStatusNot(FarmerStatus status);
}

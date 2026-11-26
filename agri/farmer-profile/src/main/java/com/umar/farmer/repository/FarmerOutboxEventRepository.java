package com.umar.farmer.repository;

import com.umar.farmer.domain.entity.FarmerOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FarmerOutboxEventRepository extends JpaRepository<FarmerOutboxEventEntity, UUID> {
    List<FarmerOutboxEventEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);
}

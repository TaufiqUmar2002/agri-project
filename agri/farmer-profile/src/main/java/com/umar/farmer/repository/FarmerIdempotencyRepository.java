package com.umar.farmer.repository;

import com.umar.farmer.domain.entity.FarmerIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface FarmerIdempotencyRepository extends JpaRepository<FarmerIdempotencyEntity, FarmerIdempotencyEntity.IdemKey> {
    Optional<FarmerIdempotencyEntity> findByIdempotencyKeyAndOperationAndExpiresAtAfter(
            String idempotencyKey, String operation, Instant now);
}

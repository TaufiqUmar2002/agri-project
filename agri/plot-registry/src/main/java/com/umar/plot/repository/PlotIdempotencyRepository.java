package com.umar.plot.repository;

import com.umar.plot.domain.entity.PlotIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PlotIdempotencyRepository extends JpaRepository<PlotIdempotencyEntity, PlotIdempotencyEntity.IdemKey> {
    Optional<PlotIdempotencyEntity> findByIdempotencyKeyAndOperationAndExpiresAtAfter(
            String idempotencyKey, String operation, Instant now);
}

package com.umar.integration.repository;

import com.umar.integration.domain.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {
    Optional<IdempotencyRecordEntity> findByOperationAndIdempotencyKey(String operation, String idempotencyKey);
}

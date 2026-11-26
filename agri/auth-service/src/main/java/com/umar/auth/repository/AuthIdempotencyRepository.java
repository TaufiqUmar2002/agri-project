package com.umar.auth.repository;

import com.umar.auth.domain.entity.AuthIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthIdempotencyRepository extends JpaRepository<AuthIdempotencyEntity, AuthIdempotencyEntity.IdemKey> {
    Optional<AuthIdempotencyEntity> findByIdempotencyKeyAndOperationAndExpiresAtAfter(
            String idempotencyKey, String operation, Instant now);
}

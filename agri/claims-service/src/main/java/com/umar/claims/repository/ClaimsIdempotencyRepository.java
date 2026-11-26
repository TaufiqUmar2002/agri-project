package com.umar.claims.repository;

import com.umar.claims.domain.ClaimsIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimsIdempotencyRepository extends JpaRepository<ClaimsIdempotencyEntity, String> {
}

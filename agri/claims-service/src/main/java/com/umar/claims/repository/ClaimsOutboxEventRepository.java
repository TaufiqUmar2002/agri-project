package com.umar.claims.repository;

import com.umar.claims.domain.ClaimsOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimsOutboxEventRepository extends JpaRepository<ClaimsOutboxEventEntity, UUID> {
}

package com.umar.audit.repository;

import com.umar.audit.domain.VerificationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationRunRepository extends JpaRepository<VerificationRunEntity, UUID> {
    Optional<VerificationRunEntity> findFirstByOrderByCompletedAtDesc();
}

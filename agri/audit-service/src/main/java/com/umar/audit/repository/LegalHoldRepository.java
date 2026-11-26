package com.umar.audit.repository;

import com.umar.audit.domain.LegalHoldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalHoldRepository extends JpaRepository<LegalHoldEntity, UUID> {
    Optional<LegalHoldEntity> findByHoldId(String holdId);
    List<LegalHoldEntity> findByStatus(String status);
}

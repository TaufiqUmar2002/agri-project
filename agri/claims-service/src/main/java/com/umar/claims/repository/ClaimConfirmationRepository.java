package com.umar.claims.repository;

import com.umar.claims.domain.ClaimConfirmationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimConfirmationRepository extends JpaRepository<ClaimConfirmationEntity, String> {
}

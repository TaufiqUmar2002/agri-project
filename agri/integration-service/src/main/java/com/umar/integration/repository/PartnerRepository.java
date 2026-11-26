package com.umar.integration.repository;

import com.umar.integration.domain.entity.PartnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartnerRepository extends JpaRepository<PartnerEntity, String> {
    Optional<PartnerEntity> findByRegistrationNumberIgnoreCase(String registrationNumber);
    Optional<PartnerEntity> findByLegalNameIgnoreCase(String legalName);
    Optional<PartnerEntity> findByTenantId(String tenantId);
}

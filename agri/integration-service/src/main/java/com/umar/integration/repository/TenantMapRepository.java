package com.umar.integration.repository;

import com.umar.integration.domain.entity.TenantMapEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMapRepository extends JpaRepository<TenantMapEntryEntity, String> {
}

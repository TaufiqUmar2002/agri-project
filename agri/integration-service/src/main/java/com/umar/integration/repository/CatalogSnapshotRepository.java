package com.umar.integration.repository;

import com.umar.integration.domain.entity.CatalogSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSnapshotRepository extends JpaRepository<CatalogSnapshotEntity, Long> {
}

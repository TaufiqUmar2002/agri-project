package com.umar.integration.repository;

import com.umar.integration.domain.entity.ExtensionVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExtensionVersionRepository extends JpaRepository<ExtensionVersionEntity, String> {
    Optional<ExtensionVersionEntity> findByExtensionIdAndVersion(String extensionId, String version);
    List<ExtensionVersionEntity> findByExtensionIdOrderByCreatedAtDesc(String extensionId);
    List<ExtensionVersionEntity> findByOwnerIdAndNameIgnoreCase(String ownerId, String name);
    List<ExtensionVersionEntity> findByExtensionPoint(String extensionPoint);
}

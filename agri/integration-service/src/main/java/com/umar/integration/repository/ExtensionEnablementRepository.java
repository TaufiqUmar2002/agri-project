package com.umar.integration.repository;

import com.umar.integration.domain.entity.ExtensionEnablementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtensionEnablementRepository extends JpaRepository<ExtensionEnablementEntity, String> {
    List<ExtensionEnablementEntity> findByHookIdAndActiveTrueOrderByPriorityAsc(String hookId);
    List<ExtensionEnablementEntity> findByActiveTrue();
    List<ExtensionEnablementEntity> findByExtensionIdAndExtensionVersionAndActiveTrue(String extensionId, String version);
    List<ExtensionEnablementEntity> findByExtensionIdAndActiveTrue(String extensionId);
    long countByHookIdAndActiveTrue(String hookId);
}

package com.umar.integration.repository;

import com.umar.integration.domain.entity.ExtensionPointEntity;
import com.umar.integration.domain.enums.HookType;
import com.umar.integration.domain.enums.PointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtensionPointRepository extends JpaRepository<ExtensionPointEntity, String> {
    List<ExtensionPointEntity> findByServiceName(String serviceName);
    List<ExtensionPointEntity> findByType(HookType type);
    List<ExtensionPointEntity> findByStatus(PointStatus status);
}

package com.umar.integration.repository;

import com.umar.integration.domain.entity.ConnectorEntity;
import com.umar.integration.domain.enums.ConnectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectorRepository extends JpaRepository<ConnectorEntity, String> {
    Optional<ConnectorEntity> findByOwnerIdAndNameIgnoreCase(String ownerId, String name);
    List<ConnectorEntity> findByOwnerId(String ownerId);
    long countByOwnerIdAndStatusNot(String ownerId, ConnectorStatus excluded);
}

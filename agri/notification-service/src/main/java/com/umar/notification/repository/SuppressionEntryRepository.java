package com.umar.notification.repository;

import com.umar.notification.domain.SuppressionEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SuppressionEntryRepository extends JpaRepository<SuppressionEntryEntity, UUID> {
    List<SuppressionEntryEntity> findByMsisdnHashAndActiveTrue(String msisdnHash);
}

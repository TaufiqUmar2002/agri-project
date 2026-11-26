package com.umar.integration.repository;

import com.umar.integration.domain.entity.InboundReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InboundReceiptRepository extends JpaRepository<InboundReceiptEntity, String> {
    List<InboundReceiptEntity> findByOwnerIdAndReceivedAtBetween(String ownerId, Instant from, Instant to);
    List<InboundReceiptEntity> findByConnectorIdAndReceivedAtBetween(String connectorId, Instant from, Instant to);
}

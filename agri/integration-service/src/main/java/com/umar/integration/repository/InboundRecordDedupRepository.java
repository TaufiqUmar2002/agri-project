package com.umar.integration.repository;

import com.umar.integration.domain.entity.InboundRecordDedupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundRecordDedupRepository extends JpaRepository<InboundRecordDedupEntity, String> {
    boolean existsByConnectorIdAndEventTypeAndExternalId(String connectorId, String eventType, String externalId);
}

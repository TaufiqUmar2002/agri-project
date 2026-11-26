package com.umar.integration.repository;

import com.umar.integration.domain.entity.WebhookDeliveryEntity;
import com.umar.integration.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, String> {
    boolean existsBySubscriptionIdAndEventId(String subscriptionId, String eventId);
    List<WebhookDeliveryEntity> findTop50BySubscriptionIdOrderByCreatedAtDesc(String subscriptionId);
    List<WebhookDeliveryEntity> findBySubscriptionIdAndCreatedAtBetween(String subscriptionId, Instant from, Instant to);
    List<WebhookDeliveryEntity> findByPartnerIdAndCreatedAtBetween(String partnerId, Instant from, Instant to);
    long countBySubscriptionIdAndStatus(String subscriptionId, DeliveryStatus status);
}

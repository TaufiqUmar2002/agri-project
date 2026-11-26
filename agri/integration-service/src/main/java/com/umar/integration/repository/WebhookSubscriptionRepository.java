package com.umar.integration.repository;

import com.umar.integration.domain.entity.WebhookSubscriptionEntity;
import com.umar.integration.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscriptionEntity, String> {
    Optional<WebhookSubscriptionEntity> findByPartnerIdAndNameIgnoreCase(String partnerId, String name);
    List<WebhookSubscriptionEntity> findByPartnerId(String partnerId);
    List<WebhookSubscriptionEntity> findByStatus(SubscriptionStatus status);
    long countByPartnerIdAndStatusNot(String partnerId, SubscriptionStatus excluded);
}

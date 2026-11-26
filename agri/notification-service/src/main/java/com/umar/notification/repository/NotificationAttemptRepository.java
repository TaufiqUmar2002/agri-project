package com.umar.notification.repository;

import com.umar.notification.domain.NotificationAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttemptEntity, UUID> {
    List<NotificationAttemptEntity> findByNotificationIdOrderByAttemptNumberAsc(String notificationId);
    Optional<NotificationAttemptEntity> findByProviderMessageId(String providerMessageId);
}

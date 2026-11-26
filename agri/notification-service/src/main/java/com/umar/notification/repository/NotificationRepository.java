package com.umar.notification.repository;

import com.umar.enums.notification.NotificationStatus;
import com.umar.notification.domain.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    Optional<NotificationEntity> findByNotificationId(String notificationId);
    Optional<NotificationEntity> findFirstByDedupeKeyAndCreatedAtAfter(String dedupeKey, Instant after);
    List<NotificationEntity> findByFarmerIdOrderByCreatedAtDesc(String farmerId);
    List<NotificationEntity> findByStatusOrderByCreatedAtAsc(NotificationStatus status);
    List<NotificationEntity> findByBroadcastId(String broadcastId);
}

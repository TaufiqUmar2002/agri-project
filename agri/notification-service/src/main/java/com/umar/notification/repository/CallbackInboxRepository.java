package com.umar.notification.repository;

import com.umar.notification.domain.CallbackInboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CallbackInboxRepository extends JpaRepository<CallbackInboxEntity, UUID> {
    boolean existsByNonce(String nonce);
}

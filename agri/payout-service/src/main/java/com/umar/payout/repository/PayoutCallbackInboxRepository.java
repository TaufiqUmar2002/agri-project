package com.umar.payout.repository;

import com.umar.payout.domain.PayoutCallbackInboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayoutCallbackInboxRepository extends JpaRepository<PayoutCallbackInboxEntity, UUID> {
    Optional<PayoutCallbackInboxEntity> findByProviderAndNonce(String provider, String nonce);
}

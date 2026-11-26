package com.umar.billing.repository;

import com.umar.billing.entity.CallbackInboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CallbackInboxRepository extends JpaRepository<CallbackInboxEntity, UUID> {
    Optional<CallbackInboxEntity> findByProviderAndProviderTxnId(String provider, String providerTxnId);
}

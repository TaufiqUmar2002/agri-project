package com.umar.payout.repository;

import com.umar.payout.domain.PayoutOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayoutOutboxEventRepository extends JpaRepository<PayoutOutboxEventEntity, UUID> {
}

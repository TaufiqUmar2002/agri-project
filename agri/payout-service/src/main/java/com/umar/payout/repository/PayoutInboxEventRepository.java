package com.umar.payout.repository;

import com.umar.payout.domain.PayoutInboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutInboxEventRepository extends JpaRepository<PayoutInboxEventEntity, String> {
}

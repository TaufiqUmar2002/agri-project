package com.umar.payout.repository;

import com.umar.enums.payout.ProviderStatus;
import com.umar.payout.domain.ProviderConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderConnectionRepository extends JpaRepository<ProviderConnectionEntity, String> {
    Optional<ProviderConnectionEntity> findFirstByNetworkAndStatusOrderByPriorityOrderAsc(String network, ProviderStatus status);
    List<ProviderConnectionEntity> findByProviderIgnoreCase(String provider);
}

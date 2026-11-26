package com.umar.policy.repository;

import com.umar.policy.entity.QuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<QuoteEntity, String> {
    Optional<QuoteEntity> findFirstByFarmerIdAndPlotIdAndProductIdAndInputsHashAndValidUntilAfterAndUsedByPolicyIdIsNull(
            UUID farmerId, String plotId, String productId, String inputsHash, Instant now);
}

package com.umar.farmer.repository;

import com.umar.enums.farmer.WalletStatus;
import com.umar.farmer.domain.entity.FarmerWalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmerWalletRepository extends JpaRepository<FarmerWalletEntity, UUID> {
    List<FarmerWalletEntity> findByFarmerIdOrderByCreatedAtAsc(UUID farmerId);
    Optional<FarmerWalletEntity> findByWalletIdAndFarmerId(UUID walletId, UUID farmerId);
    Optional<FarmerWalletEntity> findByFarmerIdAndWalletNumberHashAndStatusNot(
            UUID farmerId, String walletNumberHash, WalletStatus status);
    List<FarmerWalletEntity> findByWalletNumberHashAndFarmerIdNotAndStatusNotIn(
            String walletNumberHash, UUID farmerId, Collection<WalletStatus> excluded);
    long countByFarmerIdAndStatusNot(UUID farmerId, WalletStatus status);
    long countByFarmerIdAndCreatedAtAfter(UUID farmerId, Instant after);
}

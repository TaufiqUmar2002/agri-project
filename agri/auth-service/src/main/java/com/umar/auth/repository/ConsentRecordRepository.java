package com.umar.auth.repository;

import com.umar.auth.domain.entity.ConsentRecordEntity;
import com.umar.enums.auth.ConsentPurpose;
import com.umar.enums.auth.ConsentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecordEntity, UUID> {
    Optional<ConsentRecordEntity> findFirstByUserIdAndPurposeAndTextVersionAndStatusOrderByGrantedAtDesc(
            UUID userId, ConsentPurpose purpose, String textVersion, ConsentStatus status);
    Optional<ConsentRecordEntity> findFirstByUserIdAndPurposeAndStatusOrderByGrantedAtDesc(
            UUID userId, ConsentPurpose purpose, ConsentStatus status);
    List<ConsentRecordEntity> findByUserIdOrderByGrantedAtDesc(UUID userId);
}

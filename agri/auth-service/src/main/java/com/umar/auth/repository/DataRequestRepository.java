package com.umar.auth.repository;

import com.umar.auth.domain.entity.DataRequestEntity;
import com.umar.enums.auth.DataRequestStatus;
import com.umar.enums.auth.DataRequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DataRequestRepository extends JpaRepository<DataRequestEntity, UUID> {
    Optional<DataRequestEntity> findFirstByUserIdAndTypeAndStatusIn(
            UUID userId, DataRequestType type, Collection<DataRequestStatus> statuses);
    long countByUserIdAndRequestedAtAfter(UUID userId, Instant after);
}

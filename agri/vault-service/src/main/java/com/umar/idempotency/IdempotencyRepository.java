package com.umar.idempotency;


import com.umar.entity.dataProtection.IdempotencyRecord;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO idempotency_record (
            id,
            caller_service,
            operation,
            idempotency_key,
            request_fingerprint,
            status,
            processing_owner,
            lease_until,
            created_at,
            updated_at,
            expires_at
        )
        VALUES (
            :id,
            :caller,
            :operation,
            :key,
            :fingerprint,
            'IN_PROGRESS',
            :owner,
            :leaseUntil,
            :now,
            :now,
            :expiresAt
        )
        ON CONFLICT (
            caller_service,
            operation,
            idempotency_key
        )
        DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("caller") String caller,
            @Param("operation") String operation,
            @Param("key") UUID key,
            @Param("fingerprint") byte[] fingerprint,
            @Param("owner") String owner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now,
            @Param("expiresAt") Instant expiresAt);

    @Query("""
        SELECT r
        FROM IdempotencyRecord r
        WHERE r.callerService = :caller
          AND r.operation = :operation
          AND r.idempotencyKey = :key
        """)
    Optional<IdempotencyRecord> findByScopeAndKey(
            @Param("caller") String caller,
            @Param("operation") String operation,
            @Param("key") UUID key);

    @Modifying
    @Query(value = """
        UPDATE idempotency_record
        SET status = 'COMPLETED',
            response_payload = :response,
            http_status = :httpStatus,
            completed_at = :now,
            updated_at = :now,
            processing_owner = NULL,
            lease_until = NULL
        WHERE id = :id
          AND status = 'IN_PROGRESS'
          AND processing_owner = :owner
        """, nativeQuery = true)
    int complete(
            @Param("id") UUID id,
            @Param("owner") String owner,
            @Param("response") String response,
            @Param("httpStatus") int httpStatus,
            @Param("now") Instant now);

    @Modifying
    @Query(value = """
        UPDATE idempotency_record
        SET status = 'FAILED',
            updated_at = :now,
            processing_owner = NULL,
            lease_until = NULL
        WHERE id = :id
          AND status = 'IN_PROGRESS'
          AND processing_owner = :owner
        """, nativeQuery = true)
    int markFailed(
            @Param("id") UUID id,
            @Param("owner") String owner,
            @Param("now") Instant now);

    @Modifying
    @Query(value = """
        UPDATE idempotency_record
        SET processing_owner = :newOwner,
            lease_until = :leaseUntil,
            updated_at = :now
        WHERE id = :id
          AND status = 'IN_PROGRESS'
          AND lease_until < :now
        """, nativeQuery = true)
    int acquireExpiredLease(
            @Param("id") UUID id,
            @Param("newOwner") String newOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    @Modifying
    @Query("""
        DELETE FROM IdempotencyRecord r
        WHERE r.expiresAt < :now
        """)
    int deleteExpired(@Param("now") Instant now);
}

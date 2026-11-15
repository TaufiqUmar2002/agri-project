package com.umar.entity.dataProtection;


import com.umar.idempotency.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity
@Table(
        name = "idempotency_record",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency_scope",
                        columnNames = {
                                "caller_service",
                                "operation",
                                "idempotency_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_idempotency_expiry",
                        columnList = "expires_at"
                )
        }
)
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "caller_service", nullable = false, length = 150)
    private String callerService;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_fingerprint",
            nullable = false,
            columnDefinition = "bytea")
    private byte[] requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyStatus status;

    @Column(name = "response_payload", columnDefinition = "text")
    private String responsePayload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "processing_owner", length = 100)
    private String processingOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

}

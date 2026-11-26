package com.umar.farmer.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "farmer_idempotency_records")
@IdClass(FarmerIdempotencyEntity.IdemKey.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmerIdempotencyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Id
    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdemKey implements Serializable {
        private String idempotencyKey;
        private String operation;
    }
}

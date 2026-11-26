package com.umar.iot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "iot_idempotency")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IotIdempotencyEntity {
    @Id @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(nullable = false) private String operation;
    @Column(name = "request_hash", nullable = false) private String requestHash;
    @Column(name = "response_status", nullable = false) private int responseStatus;
    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT") private String responseBody;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

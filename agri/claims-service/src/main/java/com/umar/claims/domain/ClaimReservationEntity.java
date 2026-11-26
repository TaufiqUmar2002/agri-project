package com.umar.claims.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "claim_reservations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimReservationEntity {
    @Id
    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String status; // RESERVED | PAID | RELEASED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

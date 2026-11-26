package com.umar.claims.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "claim_confirmations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimConfirmationEntity {
    @Id
    @Column(name = "confirmation_id")
    private String confirmationId;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @Column(name = "farmer_id", nullable = false)
    private String farmerId;

    @Column(name = "step_up_token", nullable = false)
    private String stepUpToken;

    @Column(name = "acknowledged_amount_minor", nullable = false)
    private long acknowledgedAmountMinor;

    @Column(nullable = false)
    private String channel;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;
}

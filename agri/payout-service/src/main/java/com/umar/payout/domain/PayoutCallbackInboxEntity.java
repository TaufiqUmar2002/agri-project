package com.umar.payout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_callback_inbox")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutCallbackInboxEntity {
    @Id
    @Column(name = "inbox_id")
    private UUID inboxId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String nonce;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "body_hash", nullable = false)
    private String bodyHash;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private String status;
}

package com.umar.payout.domain;

import com.umar.enums.payout.ProviderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payout_provider_connections")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderConnectionEntity {
    @Id
    @Column(name = "connection_id")
    private String connectionId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String network;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderStatus status;

    @Column(name = "priority_order", nullable = false)
    private int priorityOrder;

    @Column(name = "last_probe_at")
    private Instant lastProbeAt;

    @Column(name = "last_probe_ok", nullable = false)
    private boolean lastProbeOk;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version_num", nullable = false)
    private int versionNum;
}

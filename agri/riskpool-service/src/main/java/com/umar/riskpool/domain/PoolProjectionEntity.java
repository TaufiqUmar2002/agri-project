package com.umar.riskpool.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "pool_projections")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PoolProjectionEntity {
    @Id @Column(name = "event_id") private String eventId;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Column(name = "region_code", nullable = false) private String regionCode;
    @Column(nullable = false) private String peril;
    @Column(name = "indicated_payout_minor", nullable = false) private long indicatedPayoutMinor;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}

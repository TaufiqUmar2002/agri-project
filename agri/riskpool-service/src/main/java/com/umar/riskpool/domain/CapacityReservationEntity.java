package com.umar.riskpool.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "pool_capacity_reservations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CapacityReservationEntity {
    @Id @Column(name = "reservation_id") private String reservationId;
    @Column(name = "policy_id", nullable = false, unique = true) private String policyId;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Column(name = "region_code", nullable = false) private String regionCode;
    @Column(nullable = false) private String peril;
    @Column(nullable = false) private String season;
    @Column(name = "sum_insured_minor", nullable = false) private long sumInsuredMinor;
    @Column(nullable = false) private String currency;
    @Column(nullable = false) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
}

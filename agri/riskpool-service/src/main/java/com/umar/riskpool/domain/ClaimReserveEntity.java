package com.umar.riskpool.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "pool_claim_reserves")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimReserveEntity {
    @Id @Column(name = "claim_id") private String claimId;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Column(name = "policy_id", nullable = false) private String policyId;
    @Column(name = "reserved_minor", nullable = false) private long reservedMinor;
    @Column(name = "paid_minor", nullable = false) private long paidMinor;
    @Column(nullable = false) private String currency;
    @Column(nullable = false) private boolean released;
}

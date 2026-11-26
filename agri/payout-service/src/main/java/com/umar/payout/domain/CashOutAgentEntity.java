package com.umar.payout.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payout_cash_out_agents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CashOutAgentEntity {
    @Id
    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "area_code", nullable = false)
    private String areaCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "max_single_minor", nullable = false)
    private long maxSingleMinor;
}

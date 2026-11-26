package com.umar.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "agent_floats")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentFloatEntity {
    @Id
    @Column(name = "agent_id", length = 64)
    private String agentId;
    @Column(name = "limit_minor", nullable = false)
    private Long limitMinor;
    @Column(name = "balance_minor", nullable = false)
    private Long balanceMinor;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;
    @Column(name = "collector_active", nullable = false)
    @Builder.Default
    private boolean collectorActive = true;
}

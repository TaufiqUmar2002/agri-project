package com.umar.trigger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "trigger_event_summary")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TriggerEventSummaryEntity {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rule_version", nullable = false)
    private Integer ruleVersion;

    @Column(nullable = false)
    private String peril;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "window_id", nullable = false)
    private String windowId;

    @Column(nullable = false)
    private String status;

    @Column(name = "affected_policies", nullable = false)
    private int affectedPolicies;

    @Column(name = "breached_policies", nullable = false)
    private int breachedPolicies;

    @Column(name = "indicated_payout_minor", nullable = false)
    private long indicatedPayoutMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.umar.trigger.domain;

import com.umar.enums.trigger.EvaluationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trigger_evaluations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TriggerEvaluationEntity {
    @Id
    @Column(name = "evaluation_id")
    private String evaluationId;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rule_version", nullable = false)
    private Integer ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false)
    private EvaluationType evaluationType;

    @Column(name = "region_codes_csv", nullable = false, columnDefinition = "TEXT")
    private String regionCodesCsv;

    @Column(name = "window_id", nullable = false)
    private String windowId;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Column(nullable = false)
    private String status;

    @Column(name = "evaluated_count", nullable = false)
    private int evaluatedCount;

    @Column(name = "breached_count", nullable = false)
    private int breachedCount;

    @Column(name = "review_required_count", nullable = false)
    private int reviewRequiredCount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}

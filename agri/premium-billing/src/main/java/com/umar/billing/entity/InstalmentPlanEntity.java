package com.umar.billing.entity;

import com.umar.enums.billing.PlanStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "instalment_plans")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InstalmentPlanEntity {
    @Id
    @Column(name = "plan_id", length = 40)
    private String planId;
    @Column(name = "plan_code", nullable = false, length = 40)
    private String planCode;
    @Column(name = "version_num", nullable = false)
    private Integer versionNum;
    @Column(name = "name_json", nullable = false, columnDefinition = "TEXT")
    private String nameJson;
    @Column(name = "scope_json", nullable = false, columnDefinition = "TEXT")
    private String scopeJson;
    @Column(name = "schedule_json", nullable = false, columnDefinition = "TEXT")
    private String scheduleJson;
    @Column(name = "deposit_percent", nullable = false)
    private Double depositPercent;
    @Column(name = "activation_threshold_percent", nullable = false)
    private Double activationThresholdPercent;
    @Column(name = "min_instalment_minor", nullable = false)
    private Long minInstalmentMinor;
    @Column(name = "grace_json", nullable = false, columnDefinition = "TEXT")
    private String graceJson;
    @Column(name = "lapse_json", nullable = false, columnDefinition = "TEXT")
    private String lapseJson;
    @Column(name = "reinstatement_json", nullable = false, columnDefinition = "TEXT")
    private String reinstatementJson;
    @Column(name = "reminders_json", nullable = false, columnDefinition = "TEXT")
    private String remindersJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PlanStatus status;
    @Column(name = "plan_hash", nullable = false, length = 64)
    private String planHash;
    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;
    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

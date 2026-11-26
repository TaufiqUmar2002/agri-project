package com.umar.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "subsidy_schemes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubsidySchemeEntity {
    @Id
    @Column(name = "scheme_code", length = 40)
    private String schemeCode;

    @Column(name = "funder", nullable = false, length = 120)
    private String funder;

    @Column(name = "percent_bps", nullable = false)
    private Integer percentBps; // e.g., 3000 = 30%

    @Column(name = "cap_minor")
    private Long capMinor;

    @Column(name = "budget_minor", nullable = false)
    private Long budgetMinor;

    @Column(name = "reserved_minor", nullable = false)
    @Builder.Default
    private Long reservedMinor = 0L;

    @Column(name = "region_codes_csv", columnDefinition = "TEXT")
    private String regionCodesCsv;

    @Column(name = "crop_codes_csv", columnDefinition = "TEXT")
    private String cropCodesCsv;

    @Column(name = "group_ids_csv", columnDefinition = "TEXT")
    private String groupIdsCsv;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}

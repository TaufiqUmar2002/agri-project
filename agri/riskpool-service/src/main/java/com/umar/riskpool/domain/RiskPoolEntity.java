package com.umar.riskpool.domain;

import com.umar.enums.riskpool.PoolStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "risk_pools")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskPoolEntity {
    @Id @Column(name = "pool_id") private String poolId;
    @Column(name = "pool_code", nullable = false, unique = true) private String poolCode;
    @Column(name = "name_json", nullable = false, columnDefinition = "TEXT") private String nameJson;
    @Column(name = "insurer_ref", nullable = false) private String insurerRef;
    @Column(name = "country_code", nullable = false) private String countryCode;
    @Column(nullable = false) private String currency;
    @Column(name = "mappings_json", nullable = false, columnDefinition = "TEXT") private String mappingsJson;
    @Column(name = "accounting_json", nullable = false, columnDefinition = "TEXT") private String accountingJson;
    @Column(name = "limits_json", nullable = false, columnDefinition = "TEXT") private String limitsJson;
    @Column(name = "alerts_json", nullable = false, columnDefinition = "TEXT") private String alertsJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PoolStatus status;
    @Column(name = "config_hash", nullable = false) private String configHash;
    @Column(name = "version_num", nullable = false) private int versionNum;
    @Column(name = "alert_version", nullable = false) private int alertVersion;
    @Column(name = "author_id", nullable = false) private String authorId;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

package com.umar.riskpool.domain;

import com.umar.enums.riskpool.TreatyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "reinsurance_treaties")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReinsuranceTreatyEntity {
    @Id @Column(name = "treaty_id") private String treatyId;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Column(name = "treaty_code", nullable = false) private String treatyCode;
    @Column(name = "version_num", nullable = false) private int versionNum;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TreatyType type;
    @Column(name = "placements_json", nullable = false, columnDefinition = "TEXT") private String placementsJson;
    @Column(name = "period_json", nullable = false, columnDefinition = "TEXT") private String periodJson;
    @Column(name = "scope_json", nullable = false, columnDefinition = "TEXT") private String scopeJson;
    @Column(name = "terms_json", nullable = false, columnDefinition = "TEXT") private String termsJson;
    @Column(name = "settlement_json", nullable = false, columnDefinition = "TEXT") private String settlementJson;
    @Column(name = "wording_token", nullable = false) private String wordingToken;
    @Column(name = "treaty_hash", nullable = false) private String treatyHash;
    @Column(nullable = false) private String status;
    @Column(name = "author_id", nullable = false) private String authorId;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

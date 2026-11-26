package com.umar.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "sponsor_accounts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SponsorAccountEntity {
    @Id
    @Column(name = "sponsor_id", length = 40)
    private String sponsorId;
    @Column(name = "name", nullable = false, length = 120)
    private String name;
    @Column(name = "status", nullable = false, length = 24)
    private String status; // ACTIVE | SUSPENDED | PENDING
    @Column(name = "credit_limit_minor", nullable = false)
    @Builder.Default
    private Long creditLimitMinor = 0L;
    @Column(name = "used_minor", nullable = false)
    @Builder.Default
    private Long usedMinor = 0L;
    @Column(name = "allowed_methods_csv", nullable = false, length = 120)
    private String allowedMethodsCsv;
    @Column(name = "bank_token", length = 80)
    private String bankToken;
    @Column(name = "masked_bank", length = 64)
    private String maskedBank;
}

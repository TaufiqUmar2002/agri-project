package com.umar.farmer.domain.entity;

import com.umar.enums.auth.KycTier;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eligibility_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityRecordEntity {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_tier", nullable = false, length = 16)
    private KycTier kycTier;

    @Column(name = "legal_name_token", nullable = false, length = 128)
    private String legalNameToken;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;
}

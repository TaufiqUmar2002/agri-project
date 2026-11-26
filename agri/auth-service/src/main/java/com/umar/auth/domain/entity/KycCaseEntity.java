package com.umar.auth.domain.entity;

import com.umar.enums.auth.IdType;
import com.umar.enums.auth.KycCaseStatus;
import com.umar.enums.auth.KycFlag;
import com.umar.enums.auth.KycTier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "kyc_cases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycCaseEntity {

    @Id
    @Column(name = "kyc_id", nullable = false, updatable = false)
    private UUID kycId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 32)
    private IdType idType;

    @Column(name = "id_number_token", nullable = false, length = 128)
    private String idNumberToken;

    @Column(name = "id_number_hash", nullable = false, length = 128)
    private String idNumberHash;

    @Column(name = "front_doc_token", nullable = false, length = 128)
    private String frontDocToken;

    @Column(name = "back_doc_token", length = 128)
    private String backDocToken;

    @Column(name = "selfie_doc_token", nullable = false, length = 128)
    private String selfieDocToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_tier", nullable = false, length = 16)
    private KycTier targetTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private KycCaseStatus status;

    @Column(name = "match_score", precision = 5, scale = 4)
    private BigDecimal matchScore;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(name = "flags_csv", length = 512)
    private String flagsCsv;

    @Column(name = "reason_codes_csv", length = 512)
    private String reasonCodesCsv;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "delegation_id")
    private UUID delegationId;

    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "second_reviewer_id", length = 64)
    private String secondReviewerId;

    @Column(name = "second_review_pending", nullable = false)
    private boolean secondReviewPending;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "lock_holder", length = 64)
    private String lockHolder;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public Set<KycFlag> getFlags() {
        if (flagsCsv == null || flagsCsv.isBlank()) {
            return EnumSet.noneOf(KycFlag.class);
        }
        return Arrays.stream(flagsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(KycFlag::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(KycFlag.class)));
    }

    public void setFlags(Collection<KycFlag> flags) {
        if (flags == null || flags.isEmpty()) {
            this.flagsCsv = null;
        } else {
            this.flagsCsv = flags.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        }
    }

    public List<String> getReasonCodes() {
        if (reasonCodesCsv == null || reasonCodesCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(reasonCodesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void setReasonCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            this.reasonCodesCsv = null;
        } else {
            this.reasonCodesCsv = String.join(",", codes);
        }
    }
}

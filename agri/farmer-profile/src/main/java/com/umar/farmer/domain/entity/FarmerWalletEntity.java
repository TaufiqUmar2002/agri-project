package com.umar.farmer.domain.entity;

import com.umar.enums.farmer.MatchBand;
import com.umar.enums.farmer.WalletFlag;
import com.umar.enums.farmer.WalletStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "farmer_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmerWalletEntity {

    @Id
    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "wallet_number_token", nullable = false, length = 128)
    private String walletNumberToken;

    @Column(name = "wallet_number_hash", nullable = false, length = 128)
    private String walletNumberHash;

    @Column(name = "masked_number", nullable = false, length = 32)
    private String maskedNumber;

    @Column(name = "provider_name_token", length = 128)
    private String providerNameToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_band", length = 16)
    private MatchBand matchBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WalletStatus status;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "flags_csv", length = 256)
    private String flagsCsv;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "second_reviewer_id", length = 64)
    private String secondReviewerId;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public Set<WalletFlag> getFlags() {
        if (flagsCsv == null || flagsCsv.isBlank()) return EnumSet.noneOf(WalletFlag.class);
        return Arrays.stream(flagsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(WalletFlag::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(WalletFlag.class)));
    }

    public void setFlags(Collection<WalletFlag> flags) {
        this.flagsCsv = (flags == null || flags.isEmpty())
                ? null : flags.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}

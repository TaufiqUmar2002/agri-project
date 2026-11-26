package com.umar.farmer.domain.entity;

import com.umar.enums.farmer.FarmSizeBand;
import com.umar.enums.farmer.FarmerStatus;
import com.umar.enums.farmer.PreferredChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "farmer_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmerProfileEntity {

    @Id
    @Column(name = "farmer_id", nullable = false, updatable = false)
    private UUID farmerId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private FarmerStatus status;

    @Column(name = "legal_name_token", nullable = false, length = 128)
    private String legalNameToken;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 20)
    private PreferredChannel preferredChannel;

    @Column(name = "quiet_start", length = 8)
    private String quietStart;

    @Column(name = "quiet_end", length = 8)
    private String quietEnd;

    @Column(name = "quiet_timezone", length = 64)
    private String quietTimezone;

    @Column(name = "country", nullable = false, length = 8)
    private String country;

    @Column(name = "district", nullable = false, length = 64)
    private String district;

    @Column(name = "ward", nullable = false, length = 64)
    private String ward;

    @Column(name = "village", length = 80)
    private String village;

    @Enumerated(EnumType.STRING)
    @Column(name = "farm_size_band", nullable = false, length = 24)
    private FarmSizeBand farmSizeBand;

    @Column(name = "primary_crops_csv", nullable = false, length = 256)
    private String primaryCropsCsv;

    @Convert(converter = com.umar.crypto.VaultEncryptedStringConverter.class)
    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    @Column(name = "enrolled_by_agent_id")
    private UUID enrolledByAgentId;

    @Column(name = "fraud_hold", nullable = false)
    private boolean fraudHold;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public List<String> getPrimaryCrops() {
        if (primaryCropsCsv == null || primaryCropsCsv.isBlank()) return List.of();
        return Arrays.stream(primaryCropsCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setPrimaryCrops(List<String> crops) {
        this.primaryCropsCsv = String.join(",", crops);
    }
}

package com.umar.integration.domain.entity;

import com.umar.enums.integration.FailMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "extension_enablements", indexes = {
        @Index(name = "idx_enable_hook_active", columnList = "hookId,active"),
        @Index(name = "idx_enable_ext", columnList = "extensionId,extensionVersion")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionEnablementEntity {

    @Id
    @Column(length = 64)
    private String enablementId;

    @Column(nullable = false, length = 64)
    private String extensionId;

    @Column(nullable = false, length = 32)
    private String extensionVersion;

    @Column(nullable = false, length = 128)
    private String hookId;

    @Lob
    private String tenantIdsJson;

    @Lob
    private String productIdsJson;

    @Lob
    private String regionCodesJson;

    @Column(nullable = false)
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FailMode failMode;

    @Column(nullable = false)
    private Integer timeoutMs;

    @Column(nullable = false)
    private Integer errorRatePercent;

    @Column(nullable = false)
    private Integer windowMinutes;

    private Integer p95LatencyMs;

    @Column(nullable = false)
    private Integer consecutiveFailures;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 128)
    private String enabledBy;

    @Column(nullable = false)
    private Instant effectiveBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

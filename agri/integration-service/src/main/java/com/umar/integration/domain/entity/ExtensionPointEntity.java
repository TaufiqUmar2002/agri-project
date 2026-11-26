package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.HookType;
import com.umar.integration.domain.enums.PointStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "extension_points", indexes = {
        @Index(name = "idx_ext_pt_service", columnList = "serviceName"),
        @Index(name = "idx_ext_pt_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionPointEntity {

    @Id
    @Column(length = 128)
    private String hookId;

    @Column(nullable = false, length = 64)
    private String serviceName;

    @Column(nullable = false, length = 32)
    private String serviceVersion;

    @Column(nullable = false, length = 32)
    private String sdkVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HookType type;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 32)
    private String currentContractVersion;

    @Column(length = 32)
    private String previousContractVersion;

    @Column(length = 32)
    private String deprecatesOn;

    @Column(length = 256)
    private String inputSchemaRef;

    @Column(length = 256)
    private String outputSchemaRef;

    @Lob
    private String dataScopesJson;

    @Column(nullable = false)
    private Integer defaultTimeoutMs;

    @Column(nullable = false)
    private Integer maxTimeoutMs;

    @Lob
    @Column(nullable = false)
    private String allowedFailModesJson;

    @Column(nullable = false)
    private Integer maxExtensionsPerScope;

    @Column(nullable = false)
    private boolean protectedPoint;

    @Column(nullable = false)
    private boolean partnerVisible;

    @Lob
    private String rulesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PointStatus status;

    @Column(nullable = false)
    private Instant lastRegisteredAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.ExtensionStatus;
import com.umar.enums.integration.FailMode;
import com.umar.enums.integration.RuntimeKind;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "extension_versions", indexes = {
        @Index(name = "idx_ext_id_ver", columnList = "extensionId,version", unique = true),
        @Index(name = "idx_ext_owner_name", columnList = "ownerId,name"),
        @Index(name = "idx_ext_point", columnList = "extensionPoint")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionVersionEntity {

    @Id
    @Column(length = 96)
    private String id; // extensionId + "@" + version or extensionId

    @Column(nullable = false, length = 64)
    private String extensionId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(nullable = false, length = 128)
    private String extensionPoint;

    @Column(nullable = false, length = 64)
    private String ownerId;

    @Column(nullable = false, length = 32)
    private String contractVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RuntimeKind runtimeType;

    @Lob
    @Column(nullable = false)
    private String runtimeConfigJson;

    @Lob
    @Column(nullable = false)
    private String dataScopeJson;

    @Column(nullable = false)
    private Integer timeoutMs;

    @Column(nullable = false)
    private Integer memoryMb;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FailMode failMode;

    @Lob
    private String secretsJson;

    @Lob
    @Column(nullable = false)
    private String testVectorsJson;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 1000)
    private String changelog;

    @Column(nullable = false, length = 128)
    private String artifactHash;

    @Column(length = 128)
    private String artifactToken;

    @Column(nullable = false, length = 24)
    private String scanResult;

    @Lob
    private String scanFindingsJson;

    @Column(nullable = false)
    private Integer testsTotal;

    @Column(nullable = false)
    private Integer testsPassed;

    @Column(nullable = false)
    private Integer testsFailed;

    @Column(nullable = false)
    private boolean deterministic;

    @Column(nullable = false)
    private Long maxLatencyMs;

    @Column(nullable = false)
    private Integer maxMemoryMb;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExtensionStatus status;

    @Column(length = 64)
    private String approvalId;

    @Column(nullable = false)
    private Integer etagVersion;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

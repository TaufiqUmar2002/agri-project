package com.umar.integration.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "invocation_records", indexes = {
        @Index(name = "idx_inv_ext_created", columnList = "extensionId,createdAt"),
        @Index(name = "idx_inv_tenant_created", columnList = "tenantId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvocationRecordEntity {

    @Id
    @Column(length = 64)
    private String invocationId;

    @Column(nullable = false, length = 128)
    private String hookId;

    @Column(nullable = false, length = 64)
    private String extensionId;

    @Column(nullable = false, length = 32)
    private String extensionVersion;

    @Column(nullable = false, length = 64)
    private String callerService;

    @Column(length = 64)
    private String tenantId;

    @Column(nullable = false, length = 128)
    private String subjectRefHash;

    @Column(nullable = false, length = 32)
    private String result;

    @Column(nullable = false, length = 32)
    private String failModeApplied;

    @Column(nullable = false)
    private Long latencyMs;

    @Column(nullable = false, length = 128)
    private String inputHash;

    @Column(nullable = false, length = 128)
    private String outputHash;

    @Column(nullable = false)
    private boolean shadowMode;

    @Column(nullable = false)
    private Instant createdAt;
}

package com.umar.integration.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "tenant_map")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMapEntryEntity {

    @Id
    @Column(length = 128)
    private String subjectRef;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(length = 64)
    private String groupId;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(nullable = false)
    private Instant updatedAt;
}

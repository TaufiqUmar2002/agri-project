package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.ConnectorAuthType;
import com.umar.enums.integration.ConnectorDirection;
import com.umar.integration.domain.enums.ConnectorStatus;
import com.umar.enums.integration.HubEnvironment;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "connectors", indexes = {
        @Index(name = "idx_conn_owner_name", columnList = "ownerId,name"),
        @Index(name = "idx_conn_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectorEntity {

    @Id
    @Column(length = 64)
    private String connectorId;

    @Column(nullable = false, length = 64)
    private String ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConnectorDirection direction;

    @Column(nullable = false, length = 64)
    private String adapterType;

    @Column(length = 128)
    private String adapterCustomRef;

    @Lob
    private String endpointJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConnectorAuthType authType;

    @Lob
    @Column(nullable = false)
    private String authJson;

    @Lob
    @Column(nullable = false)
    private String externalSchemaJson;

    @Lob
    @Column(nullable = false)
    private String mappingJson;

    @Lob
    @Column(nullable = false)
    private String canonicalTargetJson;

    @Lob
    private String piiFieldsJson;

    @Lob
    @Column(nullable = false)
    private String quotasJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HubEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConnectorStatus status;

    @Column(nullable = false, length = 128)
    private String configHash;

    @Column(nullable = false)
    private Integer recordsTested;

    @Column(nullable = false)
    private Integer mappedCount;

    @Column(nullable = false)
    private Integer rejectedCount;

    @Lob
    private String rejectionSamplesJson;

    @Column(length = 64)
    private String approvalId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

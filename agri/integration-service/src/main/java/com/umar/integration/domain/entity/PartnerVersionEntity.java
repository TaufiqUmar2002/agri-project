package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.PartnerStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "partner_versions", indexes = {
        @Index(name = "idx_partner_versions_pid", columnList = "partnerId,version")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerVersionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String partnerId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 128)
    private String partnerHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerStatus status;

    @Lob
    @Column(nullable = false)
    private String snapshotJson;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;
}

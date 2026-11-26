package com.umar.entity.auditAndAccess;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "BREAK_GLASS_REQUEST")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BreakGlassRequest {

    @Id
    private UUID id;

    private String incidentRef;

    @Column(columnDefinition = "TEXT")
    private String scope;

    private String investigatorRef;

    private String requestedBy;

    private String legalBasis;

    @Column(length = 2000)
    private String reason;

    private Integer durationMinutes;

    private Integer maxRecords;

    private String status; // PENDING_APPROVAL | ACTIVE | EXPIRED | CLOSED | REJECTED

    private String validFrom;

    private String validUntil;

    private Integer recordsUsed = 0;

    private String reviewStatus; // PENDING | COMPLETED | OVERDUE

    private String reviewDueBy;

    private UUID approvalId;

    private Instant createdAt = Instant.now();
}

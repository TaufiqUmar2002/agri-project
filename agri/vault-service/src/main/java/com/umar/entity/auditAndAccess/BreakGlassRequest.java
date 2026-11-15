package com.umar.entity.auditAndAccess;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

    private String  incidentRef;

    private String scope;

    private String investigatorRef;

    private String legalBasis;

    private String status;

    private String validUntil;

    private String recordsUsed;

    private String reviewStatus;
}

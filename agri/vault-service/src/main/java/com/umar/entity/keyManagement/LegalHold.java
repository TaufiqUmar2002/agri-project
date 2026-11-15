package com.umar.entity.keyManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "LEGAL_HOLD")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class LegalHold {

    @Id
    private UUID id;

    @Column(name = "CASE_REF")
    private String caseRef;

    @Column(name = "AUTHORITY")
    private String authority;

    @Column(name = "SCOPE")
    private String scope;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "REVIEW_DATE")
    private String reviewDate;
}

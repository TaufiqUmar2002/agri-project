package com.umar.entity.auditAndAccess;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "ACCESS_RECORD")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AccessRecord {

    @Id
    private UUID id;

    @Column(name = "CALLER")
    private String caller;

    @Column(name = "OPERATION")
    private String operation;

    @Column(name = "DATA_CLASS")
    private String dataClass;

    @Column(name = "SECRET_REF")
    private String secretRef;

    @Column(name = "PURPOSE")
    private String purpose;

    @Column(name = "CONTEXT_REF_HASH")
    private String contextRefHash;

    @Column(name = "SUBJECT_HASH")
    private String subjectHash;

    @Column(name = "COUNT")
    private String count;

    @Column(name = "OUTCOME")
    private String outcome;

    @Column(name = "REASON_CODE")
    private String reasonCode;

    @Column(name = "PREVIOUS_HASH")
    private String previousHash;

    @Column(name = "RECORD_HASH", nullable = false)
    private String hash;

    @Column(name = "TIMESTAMP")
    private String timestamp;

    @Column(name = "DAY_DATE")
    private String dayDate;
}

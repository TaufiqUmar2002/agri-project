package com.umar.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_subject_edges")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditSubjectEdgeEntity {
    @Id
    private UUID id;
    @Column(name = "subject_type", nullable = false)
    private String subjectType;
    @Column(name = "subject_id", nullable = false)
    private String subjectId;
    @Column(name = "linked_type", nullable = false)
    private String linkedType;
    @Column(name = "linked_id", nullable = false)
    private String linkedId;
    @Column(name = "record_id", nullable = false)
    private String recordId;
    @Column(name = "relation_type", nullable = false)
    private String relationType;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

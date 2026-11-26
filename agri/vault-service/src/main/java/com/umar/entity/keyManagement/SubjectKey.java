package com.umar.entity.keyManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "SUBJECT_KEY", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_SUBJECT_RETENTION_GROUP", columnNames = {"SUBJECT_REF", "RETENTION_GROUP"})
})
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class SubjectKey {

    @Id
    private UUID id;

    @Column(name = "SUBJECT_REF", nullable = false)
    private String subjectRef;

    @Column(name = "RETENTION_GROUP", nullable = false)
    private String retentionGroup;

    @Column(name = "WRAPPED_KEY", length = 2048)
    private String wrappedKey;

    @Column(name = "CLASS_KEK_VERSION")
    private String classKekVersion;

    @Column(name = "STATE", nullable = false)
    private String state; // ACTIVE | SHREDDED

    @Column(name = "CREATED_AT")
    private Instant createdAt = Instant.now();

    @Column(name = "SHREDDED_AT")
    private Instant shreddedAt;
}

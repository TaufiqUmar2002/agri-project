package com.umar.entity.keyManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "SUBJECT_KEY")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class SubjectKey {

    @Id
    private UUID id;

    @Column(name = "SUBJECT_REF")
    private String subjectRef;

    @Column(name = "RETENTION_GROUP")
    private String retentionGroup;

    @Column(name = "WRAPPED_KEY")
    private String wrappedKey;

    @Column(name = "CLASS_KEK_VERSION")
    private String classKekVersion;

    @Column(name = "STATE")
    private String state; // ACTIVE | SHREDDED
}

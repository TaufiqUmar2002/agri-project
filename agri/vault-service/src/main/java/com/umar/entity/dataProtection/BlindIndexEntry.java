package com.umar.entity.dataProtection;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "BLIND_INDEX_ENTRY")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BlindIndexEntry {

    @Id
    private UUID id;

    private String tokenId;

    @Column(name = "DATA_CLASS")
    private String dataClass;

    @Column(name = "INDEX_KEY_VERSION")
    private String indexKeyVersionId;

    @Column(name = "HASH")
    private  String indexHash;

    @Column(name = "SUBJECT_REF")
    private String subjectRef;
}

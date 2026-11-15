package com.umar.entity.keyManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "ERASURE_REGISTRY")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ErasureRegistry {

    @Id
    private UUID id;

    @Column(name = "SUBJECT_HASH")
    private String subjectHash;

    @Column(name = "GROUP_NAME")
    private String group;

    @Column(name = "SHREDDED_AT")
    private String shreddedAt;

    @Column(name = "REQUEST_ID")
    private String requestId;
}

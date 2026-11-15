package com.umar.entity.dataProtection;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "DOCUMENT")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Document {

    @Id
    private UUID id;

    private String token;

    private String dataClass;

    private String subjectRef;

    private String retentionClass;

    private String retainUntil;

    private String sha256;

    private String sizeBytes;

    private String contentType;

    private String objectRef;

    private String keyRef;

    private String metadataRef;

}

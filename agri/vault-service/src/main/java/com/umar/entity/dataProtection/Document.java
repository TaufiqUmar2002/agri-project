package com.umar.entity.dataProtection;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
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

    @Column(unique = true)
    private String token;

    private String dataClass;

    private String subjectRef;

    private String ownerRef;

    private UUID subjectKeyId;

    private String retentionClass;

    private String retentionGroup;

    private String retainUntil;

    private String sha256;

    private String sizeBytes;

    private String contentType;

    private String objectRef;

    private String replicaObjectRef;

    private String keyRef;

    private String metadataRef;

    @Column(name = "CAPTURE_META_JSON", columnDefinition = "TEXT")
    private String captureMetaJson;

    @Column(name = "CREATED_AT")
    private Instant createdAt = Instant.now();

    @Column(name = "ENCRYPTED_CONTENT", columnDefinition = "BYTEA")
    private byte[] encryptedContent;

    @Column(name = "NONCE", columnDefinition = "BYTEA")
    private byte[] nonce;

    @Column(name = "WRAPPED_DEK", columnDefinition = "BYTEA")
    private byte[] wrappedDek;
}

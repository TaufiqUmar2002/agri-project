package com.umar.entity.keyManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "SECRET_VERSION")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class SecretVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SECRET_ID", nullable = false)
    private Secret secret;

    @Column(name = "VERSION", nullable = false)
    private int version;

    @Column(name = "ALGORITHM")
    private String algorithm;

    @Column(name = "KEY_SIZE_BITS")
    private Integer keySizeBits;

    @Column(name = "KID")
    private String kid;

    @Column(name = "PUBLIC_KEY_JSON", columnDefinition = "TEXT")
    private String publicKeyJson;

    @Column(name = "CIPHERTEXT", columnDefinition = "BYTEA", nullable = false)
    private byte[] ciphertext;

    @Column(name = "NONCE", columnDefinition = "BYTEA", nullable = false)
    private byte[] nonce;

    @Column(name = "WRAPPED_DEK", columnDefinition = "BYTEA")
    private byte[] wrappedDek;

    @Column(name = "STATE", nullable = false)
    private String state = "ACTIVE"; // ACTIVE | RETIRING | RETIRED

    @Column(name = "ACTIVE_FROM")
    private Instant activeFrom = Instant.now();

    @Column(name = "RETIRES_AT")
    private Instant retiresAt;

    @Column(name = "REVEALED_AT")
    private Instant revealedAt;

    @Column(name = "EXPIRES_AT")
    private Instant expiresAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt = Instant.now();
}

package com.umar.entity.dataProtection;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "TOKEN",
        indexes = {
                @Index(name = "IDX_TOKEN_STRING", columnList = "TOKEN", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class VaultToken {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "DATA_CLASS_ID")
    private String dataClass;

    @Column(name = "SUBJECT_REF")
    private String subjectRef;

    @Column(name = "RETENTION_GROUP")
    private String retentionGroup;

    @Column(columnDefinition = "TEXT", nullable = false,name = "CIPHER_TEXT")
    private byte[] ciphertext;

    @Column(name = "NONCE")
    private byte[] nonce;

    @Column(name = "KEY_VERSION")
    private int keyVersion;

    @Column(name = "MASKED_FORM")
    private String maskedForm;

    @Column(name = "FORMAT")
    private String format;

    @Column(name = "CREATED_AT")
    private Instant createdAt;
}

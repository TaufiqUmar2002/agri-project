package com.umar.entity.dataProtection;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "BLIND_INDEX_KEY_VERSION")
@EntityListeners(AuditingEntityListener.class)
@Data
public class BlindIndexKeyVersion {

    @Id
    private UUID id;

    @Column(name = "DATA_CLASS")
    private String dataClass;

    @Column(name = "VERSION")
    private int version;

    @Column(name = "STATE")
    private String state;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    public BlindIndexKeyVersion() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
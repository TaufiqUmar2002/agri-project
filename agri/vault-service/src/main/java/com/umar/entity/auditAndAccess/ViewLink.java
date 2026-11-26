package com.umar.entity.auditAndAccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "VIEW_LINK")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ViewLink {

    @Id
    private UUID id;

    private String documentToken;

    private String viewerRefHash;

    private String viewerRole;

    private String sessionBinding;

    @Column(name = "WATERMARK_TEXT")
    private String watermarkText;

    private String expiresAt;

    private String usedAt;
}

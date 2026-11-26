package com.umar.notification.domain;

import com.umar.enums.notification.NotificationCategory;
import com.umar.enums.notification.TemplateStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_templates")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MessageTemplateEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private String language;
    @Column(nullable = false)
    private int version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationCategory category;
    @Column(nullable = false)
    private boolean mandatory;
    @Column(name = "accepts_secret", nullable = false)
    private boolean acceptsSecret;
    @Column(name = "variants_json", nullable = false, columnDefinition = "TEXT")
    private String variantsJson;
    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;
    @Column(name = "checks_json", columnDefinition = "TEXT")
    private String checksJson;
    @Column(name = "approvals_json", columnDefinition = "TEXT")
    private String approvalsJson;
    @Column(name = "provider_status")
    private String providerStatus;
    @Column(name = "current_version", nullable = false)
    private boolean currentVersion;
    @Column(name = "last_edited_by")
    private String lastEditedBy;
    @Column(name = "approved_by")
    private String approvedBy;
    private String note;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

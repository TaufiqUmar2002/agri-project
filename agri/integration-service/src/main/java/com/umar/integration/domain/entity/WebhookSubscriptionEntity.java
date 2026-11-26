package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.*;
import com.umar.enums.integration.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_subscriptions", indexes = {
        @Index(name = "idx_sub_partner_name", columnList = "partnerId,name"),
        @Index(name = "idx_sub_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookSubscriptionEntity {

    @Id
    @Column(length = 64)
    private String subscriptionId;

    @Column(nullable = false, length = 64)
    private String partnerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HubEnvironment environment;

    @Column(nullable = false, length = 500)
    private String targetUrl;

    @Lob
    @Column(nullable = false)
    private String eventTypesJson;

    @Lob
    private String filtersJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataScopeLevel dataScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryOrdering ordering;

    @Column(nullable = false)
    private Integer rateLimitPerSecond;

    @Column(nullable = false)
    private Integer batchSize;

    @Lob
    @Column(nullable = false)
    private String contactJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PauseReason pauseReason;

    @Column(nullable = false, length = 128)
    private String keyId;

    private Instant keyCreatedAt;

    @Column(length = 128)
    private String previousKeyId;

    private Instant overlapEndsAt;

    @Column(length = 32)
    private String challengeResult;

    private Instant challengeCheckedAt;

    @Column(length = 500)
    private String challengeFailureReason;

    @Column(length = 64)
    private String approvalId;

    @Column(nullable = false)
    private boolean securityBlocked;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

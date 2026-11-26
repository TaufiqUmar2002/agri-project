package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_deliveries", indexes = {
        @Index(name = "idx_dlv_sub_event", columnList = "subscriptionId,eventId"),
        @Index(name = "idx_dlv_sub_created", columnList = "subscriptionId,createdAt"),
        @Index(name = "idx_dlv_partner_created", columnList = "partnerId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryEntity {

    @Id
    @Column(length = 64)
    private String deliveryId;

    @Column(nullable = false, length = 64)
    private String subscriptionId;

    @Column(nullable = false, length = 64)
    private String partnerId;

    @Column(nullable = false, length = 128)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(length = 128)
    private String subjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status;

    @Column(nullable = false)
    private Integer attempts;

    private Integer responseCode;

    private Long latencyMs;

    private Integer payloadBytes;

    @Column(length = 128)
    private String kafkaTopic;

    private Integer kafkaPartition;

    private Long kafkaOffset;

    @Column(length = 64)
    private String reasonCode;

    private Instant nextRetryAt;

    @Column(nullable = false)
    private boolean replay;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

package com.umar.auth.domain.entity;

import com.umar.enums.auth.DataRequestStatus;
import com.umar.enums.auth.DataRequestType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRequestEntity {

    @Id
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private DataRequestType type;

    @Column(name = "categories_csv", nullable = false, length = 256)
    private String categoriesCsv;

    @Column(name = "delivery_channel", length = 20)
    private String deliveryChannel;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DataRequestStatus status;

    @Column(name = "cooling_off_until")
    private Instant coolingOffUntil;

    @Column(name = "due_by", nullable = false)
    private Instant dueBy;

    @Column(name = "retained_categories_csv", length = 512)
    private String retainedCategoriesCsv;

    @Column(name = "completed_parts_csv", length = 512)
    private String completedPartsCsv;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}

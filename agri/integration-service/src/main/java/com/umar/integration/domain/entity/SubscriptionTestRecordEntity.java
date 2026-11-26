package com.umar.integration.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "subscription_test_records", indexes = {
        @Index(name = "idx_test_sub_time", columnList = "subscriptionId,testedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionTestRecordEntity {

    @Id
    @Column(length = 64)
    private String testId;

    @Column(nullable = false, length = 64)
    private String subscriptionId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 32)
    private String result;

    private Integer responseCode;

    private Long latencyMs;

    @Column(length = 2048)
    private String responseSnippet;

    @Column(nullable = false)
    private Instant testedAt;
}

package com.umar.iot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "iot_rejected_readings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IotRejectedReadingEntity {
    @Id @Column(name = "reject_id") private String rejectId;
    @Column(name = "device_id", nullable = false) private String deviceId;
    @Column(nullable = false) private String channel;
    @Column(name = "raw_value") private Double rawValue;
    @Column(name = "reason_code", nullable = false) private String reasonCode;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
}

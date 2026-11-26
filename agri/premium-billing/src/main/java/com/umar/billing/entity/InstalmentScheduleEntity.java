package com.umar.billing.entity;

import com.umar.enums.billing.InstalmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instalment_schedules")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InstalmentScheduleEntity {
    @Id
    @Column(name = "schedule_id")
    private UUID scheduleId;
    @Column(name = "invoice_id", nullable = false, length = 40)
    private String invoiceId;
    @Column(name = "seq", nullable = false)
    private Integer seq;
    @Column(name = "due_date", nullable = false)
    private Instant dueDate;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "paid_minor", nullable = false)
    @Builder.Default
    private Long paidMinor = 0L;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InstalmentStatus status;
    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;
}

package com.umar.billing.entity;

import com.umar.enums.billing.InvoiceLineCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceLineEntity {
    @Id
    @Column(name = "line_id")
    private UUID lineId;
    @Column(name = "invoice_id", nullable = false, length = 40)
    private String invoiceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 24)
    private InvoiceLineCode code;
    @Column(name = "label", nullable = false, length = 120)
    private String label;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "refundable", nullable = false)
    private boolean refundable;
    @Column(name = "funder", length = 120)
    private String funder;
}

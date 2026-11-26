package com.umar.payload.request.billing;

import com.umar.enums.billing.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class RecordOfflinePaymentRequest {
    @NotBlank
    private String invoiceId;
    @NotNull
    private PaymentMethod method; // AGENT_CASH | BANK_TRANSFER
    @NotNull
    @Positive
    private Long amountMinor;
    @NotNull
    private Instant collectedAt;
    private String receiptNumber;
    private String statementLineRef;
    private List<String> evidenceTokens;
    private String note;
}

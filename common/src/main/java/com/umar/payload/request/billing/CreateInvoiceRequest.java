package com.umar.payload.request.billing;

import com.umar.enums.billing.InvoiceType;
import com.umar.enums.policy.PayerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateInvoiceRequest {
    @NotNull
    private InvoiceType type;
    private String policyId;
    private String batchId;
    @NotNull
    private PayerType payerType;
    @NotBlank
    private String payerRef;
    @NotNull
    @Positive
    private Long amountMinor;
    @NotBlank
    private String currency;
    private Instant dueDate;
    private String instalmentPlanId;
    private String reason;
}

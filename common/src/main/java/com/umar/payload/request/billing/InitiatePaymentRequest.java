package com.umar.payload.request.billing;

import com.umar.enums.billing.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitiatePaymentRequest {
    @NotBlank
    private String invoiceId;
    @NotNull
    private PaymentMethod method;
    private String provider;
    private String payerNumber;
    private Long amountMinor;
    private String instalmentPlanId;
    @NotBlank
    private String channel; // APP | USSD | WHATSAPP | AGENT | SUPPORT
}
